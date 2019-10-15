package dopenkov.weather.server

import java.util.logging.{Level, Logger}

import cats.effect._
import cats.effect.concurrent.MVar
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import dopenkov.weather.ServiceMessage.Signal
import dopenkov.weather.ServiceReply.Status.{ERROR, OK}
import dopenkov.weather.WeatherReply.Status.{AMBIGUOUS_CITY, NOT_FOUND, SERVICE_UNAVAILABLE}
import dopenkov.weather._
import dopenkov.weather.server.OpenWeatherClient.WeatherServiceError
import fs2.concurrent.Queue
import io.grpc.stub.StreamObserver
import io.grpc.{Server, ServerBuilder}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

/**
  *
  * @author Dmitry Openkov
  */
object WeatherApplication extends IOApp {
  private[this] val logger = Logger.getLogger("weather-app")

  def startServer[F[_]: ConcurrentEffect](
    stopFlag: MVar[F, Unit],
    weatherClient: OpenWeatherClient[F],
    dataProvider: DataProvider
  ): F[Server] = Sync[F].delay {
    val server: Server = ServerBuilder
      .forPort(54321)
      .addService(WeatherServiceGrpc.bindService(new WeatherServiceImpl(stopFlag, weatherClient, dataProvider), global))
      .build
      .start
    server
  }

  def executeServer(weatherClient: OpenWeatherClient[IO]): IO[ExitCode] =
    for {
      stopFlag     <- MVar[IO].empty[Unit]
      dataProvider <- DataProvider.dataProvider[IO]
      server       <- startServer[IO](stopFlag, weatherClient, dataProvider).start
      _            <- stopFlag.read
      _            <- server.cancel.start
    } yield ExitCode.Success

  override def run(args: List[String]): IO[ExitCode] = {
    import pureconfig._
    import pureconfig.generic.auto._

    case class MyConfig(openweatherApiKey: String)
    for {
      cfg <- IO { ConfigSource.default.load[MyConfig] }
      exitCode <- cfg match {
        case Left(failures) => IO { println(s"Error reading config: $failures"); ExitCode.Error }
        case Right(cfg) =>
          OpenWeatherClient
            .openWeatherClient[IO](cfg.openweatherApiKey)
            .use(client => executeServer(client))
      }
    } yield exitCode
  }

  private class WeatherServiceImpl[F[_]: ConcurrentEffect](
    stopFlag: MVar[F, Unit],
    weatherClient: OpenWeatherClient[F],
    dataProvider: DataProvider
  ) extends WeatherServiceGrpc.WeatherService {
    val F = implicitly[Effect[F]]

    override def getWeather(req: WeatherRequest): Future[WeatherReply] = {
      logger.info(s"getWeather: got request '${req}'")
      val found = dataProvider.findCity(CitySearch(req.city, req.countryCode, req.region))
      logger.info(s"getWeather: found city = '$found'")
      val replay = found match {
        case Left(numCities) => F.delay(toError(req.city, req.region, req.countryCode, numCities))
        case Right(city) =>
          for {
            resp <- weatherClient.getTemperature(city.id)
          } yield
            resp match {
              case Left(err) =>
                new WeatherReply(city.name, city.region, city.countryCode, status = toOurError(err))
              case Right(temperature) => new WeatherReply(city.name, city.region, city.countryCode, temperature.toInt)
            }
      }
      F.toIO(replay).unsafeToFuture()
    }

    private def toOurError(err: WeatherServiceError) =
      err match {
        case WeatherServiceError.NotFound => NOT_FOUND
        case _                            => SERVICE_UNAVAILABLE
      }

    private def toError(city: String, region: String, code: String, numCities: Int) =
      new WeatherReply(city, region, code, status = if (numCities == 0) NOT_FOUND else AMBIGUOUS_CITY)

    override def listWeather(responseObserver: StreamObserver[WeatherReply]): StreamObserver[WeatherRequest] =
      F.toIO(
          for {
            q <- Queue.bounded[F, Option[WeatherRequest]](20)
            observer <- F.delay(
              new StreamObserver[WeatherRequest] {
                override def onNext(value: WeatherRequest): Unit = {
                  logger.info(s"listWeather: got request for '${value.city}'")
                  F.toIO(q.enqueue1(value.some)).unsafeRunSync()
                }

                override def onError(t: Throwable): Unit = {
                  //means something unrecoverable like network error in this case
                  logger.log(Level.SEVERE, "Error getting request from client", t)
                  F.toIO(q.dequeue.dropWhile(_ => true).compile.drain).unsafeRunAsyncAndForget()
                }

                override def onCompleted(): Unit = {
                  logger.info("listWeather: onCompleted")
                  F.toIO(
                      for {
                        _    <- q.enqueue1(none[WeatherRequest])
                        list <- q.dequeue.unNoneTerminate.compile.toList
                        (notFound, found) = dataProvider
                          .findCities(list.map(r => CitySearch(r.city, r.countryCode, r.region)))
                        errors = notFound.map {
                          case (citySearch, num) =>
                            toError(citySearch.name, citySearch.region.getOrElse(""), citySearch.countryCode, num)
                        }
                        _ <- F.delay(errors.foreach(responseObserver.onNext))
                        weatherResult <- if (found.isEmpty) F.pure(Map[String, Double]().asRight[WeatherServiceError])
                        else weatherClient.getTemperature(found.map(_.id))
                        idMap = found.map(city => city.id -> city).toMap
                        replies = weatherResult match {
                          case Left(err) => List(WeatherReply(status = toOurError(err)))
                          case Right(temperatureMap) =>
                            temperatureMap.map {
                              case (id, t) =>
                                WeatherReply(
                                  city = idMap(id).name,
                                  region = idMap(id).region,
                                  countryCode = idMap(id).countryCode,
                                  temperature = t
                                )
                            }.toList
                        }
                        _ <- F.delay(replies.foreach(responseObserver.onNext))
                        _ <- F.delay(responseObserver.onCompleted())
                      } yield ()
                    )
                    .unsafeRunAsyncAndForget()
                }
              }
            )

          } yield observer
        )
        .unsafeRunSync()

    override def serviceMessage(request: ServiceMessage): Future[ServiceReply] = request.signal match {
      case Signal.STOP =>
        logger.info("STOP")
        F.toIO(stopFlag.put(())).map(_ => ServiceReply(OK)).unsafeToFuture()
      case Signal.DROP_ALL =>
        logger.info("DROP_ALL")
        Future.successful(ServiceReply(OK))
      case Signal.Unrecognized(value) =>
        logger.warning(s"Signal Unrecognized: $value")
        Future.successful(ServiceReply(ERROR))
    }
  }
}
