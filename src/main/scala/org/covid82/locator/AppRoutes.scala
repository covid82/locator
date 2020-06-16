package org.covid82.locator

import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import cats.{Applicative, Defer}

object AppRoutes {
  def monitoringRoutes[F[_] : Defer : Applicative]: HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "health" =>
        Ok("Ok")
    }
  }
}
