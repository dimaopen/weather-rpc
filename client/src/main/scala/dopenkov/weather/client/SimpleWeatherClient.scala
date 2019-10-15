package dopenkov.weather.client

import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import dopenkov.weather.ServiceMessage.Signal.{DROP_ALL, STOP}
import dopenkov.weather.WeatherServiceGrpc.WeatherServiceBlockingStub
import dopenkov.weather.{ServiceMessage, ServiceReply, WeatherRequest, WeatherServiceGrpc}
import io.grpc.{Channel, ManagedChannelBuilder}

/**
  *
  * @author Dmitry Openkov
  */
object SimpleWeatherClient extends IOApp {

  def stopServer[F[_]: Sync](stub: WeatherServiceBlockingStub): F[ServiceReply.Status] = {
    val dropMessage = new ServiceMessage(DROP_ALL)
    val stopMessage = new ServiceMessage(STOP)
    for {
      r1 <- Sync[F].delay(stub.serviceMessage(dropMessage).status)
      r2 <- if (r1.isOk) Sync[F].delay(stub.serviceMessage(stopMessage).status)
      else Sync[F].pure(r1)
    } yield r1
  }

  def readWeatherAndStopServer[F[_]: Sync](
    city: String,
    countryCode: String,
    channel: Channel
  ): F[ServiceReply.Status] = {
    val stub    = WeatherServiceGrpc.blockingStub(channel)
    val request = WeatherRequest(city, countryCode = countryCode)
    for {
      response <- Sync[F].delay(stub.getWeather(request))
      _        <- Sync[F].delay(println(s"response status is ${response.status}"))
      _ <- Sync[F].delay(
        if (response.status.isOk)
          println(
            s"temperature in ${response.city}, ${response.region}, ${response.countryCode} is ${response.temperature}"
          )
      )
      status <- stopServer(stub)
      _      <- Sync[F].delay(println(s"server shutdown: $status"))
    } yield (status)
  }

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val sync = implicitly[Sync[IO]]
    val city          = args.headOption.getOrElse("Athens")
    val countryCode   = args.drop(1).headOption.getOrElse("GR")
    (IO(
      ManagedChannelBuilder.forAddress("localhost", 54321).usePlaintext().asInstanceOf[ManagedChannelBuilder[_]].build
    ).bracketCase(channel => readWeatherAndStopServer(city, countryCode, channel))(
      (channel, exit) => {
        channel.shutdown()
        exit match {
          case ExitCase.Completed => IO.unit
          case ExitCase.Error(e)  => IO(println(s"error: ${e.getMessage}"))
          case ExitCase.Canceled  => IO(println("client cancelled"))
        }
      }
    ) >> IO(ExitCode.Success)).handleErrorWith(_ => IO(ExitCode.Error))
  }
}
