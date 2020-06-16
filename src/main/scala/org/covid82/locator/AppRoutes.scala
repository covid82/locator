package org.covid82.locator

import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import cats.{Applicative, Defer}

object AppRoutes {

  import cats.effect.Sync
  import cats.syntax.flatMap._

  def monitoringRoutes[F[_] : Sync](ripeService: RipeService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "health" =>
        ripeService.registryRef.get.flatMap {
          case None => ServiceUnavailable()
          case Some(registry) => Ok(s"Registry size: ${registry.size}")
        }
    }
  }

  import scala.util.Try
  import java.net.InetAddress

  object InetAddressVar {
    def unapply(str: String): Option[String] = if (str.isEmpty) None else {
      Try(InetAddress.getByName(str)).map(_ => str).toOption
    }
  }

  import cats.effect.{Effect, ContextShift, Timer}
  import natchez.EntryPoint

  def apiRoutes[F[_] : Effect : ContextShift : Timer](ripeService: RipeService[F])(implicit
    entryPoint: EntryPoint[F]
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    import org.http4s.circe.CirceEntityEncoder._
    import io.circe.generic.auto._
    import cats.syntax.apply._
    import cats.syntax.flatMap._
    HttpRoutes.of[F] {
      case GET -> Root / "api" / "locate" / InetAddressVar(ip) => entryPoint.root("locate").use { span =>
        ripeService.find(ip).flatMap {
          case Some(country) =>
            span.put("ip" -> ip, "country" -> country) *> Ok(Location(ip, country))
          case None =>
            span.put("ip" -> ip) *> NotFound("Not Found")
        }
      }
      case POST -> Root / "api" / "load" => entryPoint.root("load").use { span =>
        ripeService.read.flatMap { size =>
          span.put("registrySize" -> size) *> Ok(s"Loaded $size records")
        }
      }
    }
  }

  import cats.Monad
  import cats.effect.{Blocker, Sync}
  import org.http4s.{StaticFile, Header}

  def staticFilesRoute[F[_] : Sync : Monad : ContextShift](blocker: Blocker)(implicit
    entryPoint: EntryPoint[F]
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case request @ GET -> Root / "api" / "locate" => entryPoint.root("index").use { _ =>
        StaticFile.fromResource("index.html", blocker, Some(request))
          .map(_.putHeaders(Header("Content-Type", "text/html; charset=UTF-8")))
          .getOrElseF(NotFound("Not Found"))
      }
    }
  }
}
