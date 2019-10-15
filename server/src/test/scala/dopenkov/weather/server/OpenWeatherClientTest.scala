package dopenkov.weather.server

import cats.effect.IO
import org.scalatest.{MustMatchers, WordSpec}
import io.circe._
import io.circe.parser._

import scala.io.Source

/**
*
* @author Dmitry Openkov
*/
class OpenWeatherClientTest extends WordSpec with MustMatchers {
/*  implicit val cs = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val ce = IO.ioConcurrentEffect
  val openWeatherClient = OpenWeatherClient.openWeatherClient[IO]*/

  private def getJson(resource: String) = {
    parse(Source.fromResource(resource).mkString)
      .getOrElse(throw new IllegalArgumentException("cannot parse " + resource))
  }
  val singleJson = getJson("weather-json-samples/single-city-response.json")

  val multiJson = getJson("weather-json-samples/multi-city-response.json")

  "OpenWeatherClient" when {
    "get a json response for a single city" should {
      "get the temperature out of the response" in {
        OpenWeatherClient.getSingleTemperature(singleJson) mustBe Right(300.15)
      }
    }
    "get a json response for multiple cities" should {
      "get all the temperatures out of the response" in {
        val result = OpenWeatherClient.getMultiTemperature(multiJson).getOrElse(throw new Exception("wrong format"))
        result.size mustBe 3
        result("524901") mustBe -10.5
        result("703448") mustBe -11.04
        result("2643743") mustBe 7.0
      }
    }
  }


  /*openWeatherClient.use(client => IO {
    client.getSingleTemperature(singleJson.toOption.get)
  }).unsafeRunAsyncAndForget()*/



}
