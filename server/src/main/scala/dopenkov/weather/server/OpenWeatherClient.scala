package dopenkov.weather.server

import cats.effect._
import cats.instances.either._
import cats.instances.vector._
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.traverse._
import dopenkov.weather.server.OpenWeatherClient.WeatherServiceError.ProtocolError
import enumeratum._
import io.circe.Json
import org.http4s.UriTemplate.{ParamElm, ParamVarExp, PathElm, VarExp}
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.{Uri, UriTemplate}

import scala.concurrent.ExecutionContext.Implicits.global

object OpenWeatherClient {

  sealed trait WeatherServiceError extends EnumEntry

  object WeatherServiceError extends Enum[WeatherServiceError] {
    val values = findValues

    case object NotFound extends WeatherServiceError

    case object ServiceUnavailable extends WeatherServiceError

    case object ProtocolError extends WeatherServiceError

  }

  def openWeatherClient[F[_]](apiKey: String)(implicit ce: ConcurrentEffect[F]): Resource[F, OpenWeatherClient[F]] =
    BlazeClientBuilder(global)(ce).resource.map(client => new OpenWeatherClient[F](client, apiKey))



  private[server] def getSingleTemperature(json: Json): Either[WeatherServiceError, Double] =
    json.hcursor.downField("main").get[Double]("temp") match {
      case Left(_)            => ProtocolError.asLeft
      case Right(temperature) => temperature.asRight
    }

  private[server] def getSingleId(json: Json): Either[WeatherServiceError, String] =
    json.hcursor.get[Int]("id") match {
      case Left(_)            => ProtocolError.asLeft
      case Right(id) => id.toString.asRight
    }

  private[server] def getMultiTemperature(json: Json): Either[WeatherServiceError, Map[String, Double]] =
    json.hcursor.downField("list").values match {
      case Some(cities) => cities.toVector
        .map(cityJson => for {
          id <- getSingleId(cityJson)
          temp <- getSingleTemperature(cityJson)
        } yield id -> temp).sequence.map(_.toMap)
      case None => ProtocolError.asLeft
    }

}

/**
  *
  * @author Dmitry Openkov
  */
class OpenWeatherClient[F[_]: Sync](client: Client[F], appId: String) {
  import dopenkov.weather.server.OpenWeatherClient.WeatherServiceError

  val template = UriTemplate(
    authority = Some(Uri.Authority(host = Uri.RegName("api.openweathermap.org"))),
    scheme = Some(Uri.Scheme.https),
    path = List(PathElm("data"), PathElm("2.5"), VarExp("pathVar")),
    query = List(ParamVarExp("id", "cityId"), ParamElm("appid", appId))
  )

  def getTemperature(cityId: String): F[Either[WeatherServiceError, Double]] = {
    val uri: Uri = template
      .expandPath("pathVar", "weather")
      .expandQuery("cityId", cityId)
      .toUriIfPossible.get
    println(uri)
    for {
      json <- client.expect[Json](uri)
    } yield OpenWeatherClient.getSingleTemperature(json)
  }

  def getTemperature(cityIds: List[String]): F[Either[WeatherServiceError, Map[String, Double]]] = {
    val uri: Uri = template
      .expandPath("pathVar", "group")
      .expandQuery("cityId", cityIds.mkString(","))
      .toUriIfPossible.get
    println(uri)
    for {
      json <- client.expect[Json](uri)
    } yield OpenWeatherClient.getMultiTemperature(json)
  }
}
