package dopenkov.weather.client

import cats.effect._
import cats.effect.concurrent.MVar
import cats.syntax.flatMap._
import cats.syntax.functor._
import dopenkov.weather.WeatherReply.Status
import dopenkov.weather.{WeatherReply, WeatherRequest, WeatherServiceGrpc}
import io.grpc.stub.StreamObserver
import io.grpc.{Channel, ManagedChannelBuilder}

/**
  *
  * @author Dmitry Openkov
  */
object StreamingWeatherClient extends IOApp {

  def readWeather[F[_]: Sync](cities: List[(String, String)], channel: Channel, finishedFlag: MVar[IO, Unit]): F[Unit] = {
    def finishReading(): Unit =
      finishedFlag.put(()).unsafeRunAsync(_ => ())

    val stub = WeatherServiceGrpc.stub(channel)
    for {
      requestObserver <- Sync[F].delay(
        stub.listWeather(
          new StreamObserver[WeatherReply] {
            override def onNext(response: WeatherReply): Unit =
              response.status match {
                case Status.OK =>
                  println(s"temperature in ${response.city} is ${response.temperature}")
                case Status.Unrecognized(value) =>
                  println(s"unrecognized response ${value}")
                case x @ _ =>
                  println(s"error for ${response.city} is $x")
              }

            override def onError(t: Throwable): Unit = {
              finishReading()
              println(s"error~: ${t.getMessage}")
            }

            override def onCompleted(): Unit =
              finishReading()
          }
        )
      )
      _ <- Sync[F].delay(cities.foreach { case (city, countryCode) =>
        requestObserver.onNext(new WeatherRequest(city, countryCode = countryCode))
      })
      _ <- Sync[F].delay(requestObserver.onCompleted())
    } yield ()
  }

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val sync = implicitly[Sync[IO]]
    val cities =
      if (args.size < 2) List(("Rome", "IT"), ("Athens", "GR"), ("Moscow", "RU"), ("Kazan", "RU"))
      else args.zip(args.tail)
    (
      for {
        finishedFlag <- MVar[IO].empty[Unit]
        _ <- IO(
          ManagedChannelBuilder
            .forAddress("localhost", 54321)
            .usePlaintext()
            .asInstanceOf[ManagedChannelBuilder[_]]
            .build
        ).bracketCase(channel => readWeather(cities, channel, finishedFlag) >> finishedFlag.read)(
          (channel, exit) => {
            channel.shutdown()
            exit match {
              case ExitCase.Completed => IO.unit
              case ExitCase.Error(e)  => IO(println(s"error!: ${e.getMessage}"))
              case ExitCase.Canceled  => IO(println("client cancelled"))
            }
          }
        )

      } yield ExitCode.Success
    ).handleErrorWith(_ => IO(ExitCode.Error))
  }
}
