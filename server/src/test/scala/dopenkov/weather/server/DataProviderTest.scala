package dopenkov.weather.server

import cats.effect.IO
import org.scalatest.{MustMatchers, WordSpec}

/**
  *
  * @author Dmitry Openkov
  */
class DataProviderTest extends WordSpec with MustMatchers {
  val dataProvider = DataProvider.dataProvider[IO].unsafeRunSync()

  "DataProvider" when {
    "this city is the only one with this name in a country" should {
      "provide city id for city name and country code" in {
        dataProvider.findCity(CitySearch("Athens", "GR")) mustBe Right(City("Athens", "Attica", "GR", "264371"))
      }
    }
    "multiple cities in a country have this name" should {
      "give error with number of cities" in {
        dataProvider.findCity(CitySearch("Athens", "US")) mustBe Left(2)
      }
    }
    "multiple cities in a country have this name" should {
      "give city id for city name, country and region" in {
        dataProvider.findCity(CitySearch("Athens", "US", "Georgia")) mustBe
          Right(City("Athens", "Georgia", "US", "4180386"))
      }
    }
    "no this city" should {
      "give error with zero" in {
        dataProvider.findCity(CitySearch("Athens", "DE")) mustBe Left(0)
      }
    }
  }
}
