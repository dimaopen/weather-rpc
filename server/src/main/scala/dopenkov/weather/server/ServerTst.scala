package dopenkov.weather.server

import dopenkov.weather.{ServiceMessage, WeatherReply, WeatherRequest, WeatherServiceGrpc}
import io.grpc.stub.StreamObserver
import io.grpc.{Server, ServerBuilder}

import scala.concurrent.{ExecutionContext, Future}

/**
 *
 * @author Dmitry Openkov
 */
object ServerTst extends App { self =>
  private val global = ExecutionContext.global
  val server: Server = ServerBuilder
    .forPort(54321)
    .addService(WeatherServiceGrpc.bindService(new WeatherServiceImpl, global))
    .asInstanceOf[ServerBuilder[_]]
    .build
    .start
  sys.addShutdownHook {
    System.err.println("*** shutting down gRPC server since JVM is shutting down")
    self.stop()
    System.err.println("*** server shut down")
  }
  blockUntilShutdown();

  private def stop(): Unit =
    self.server.shutdown()

  private def blockUntilShutdown(): Unit =
    self.server.awaitTermination()

  private class WeatherServiceImpl extends WeatherServiceGrpc.WeatherService {
    override def getWeather(req: WeatherRequest) = {
      val reply = WeatherReply(city = req.city, temperature = -10)
      Future.successful(reply)
    }

    override def listWeather(responseObserver: StreamObserver[WeatherReply]) = ???

    override def serviceMessage(request: ServiceMessage) = ???
  }
}
