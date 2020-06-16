package org.covid82.locator

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.syntax.functor._
import cats.effect.{Sync, Resource}
import natchez.EntryPoint
import natchez.jaeger.Jaeger
import io.jaegertracing.Configuration.{SamplerConfiguration,ReporterConfiguration}
import fs2.concurrent.SignallingRef

object Main extends IOApp {

  val config: FtpConfig = FtpConfig(
    path = "/pub/stats/ripencc/delegated-ripencc-latest",
    host = "ftp.ripe.net", port = 21,
    user = "anonymous", pass = ""
  )

  def entryPoint[F[_] : Sync]: Resource[F, EntryPoint[F]] = Jaeger.entryPoint[F]("locator") { conf =>
    Sync[F].delay(conf
      .withSampler(SamplerConfiguration.fromEnv())
      .withReporter(ReporterConfiguration.fromEnv())
      .getTracer
    )
  }

  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO].use { implicit blocker =>
      entryPoint[IO].use { implicit entryPoint =>
        SignallingRef[IO, Option[IpRegistry]](Option.empty).flatMap { registryRef =>
          AppServer
            .stream[IO](RegistryReader.ftp[IO](config), registryRef)
            .compile
            .drain
            .as(ExitCode.Success)
        }
      }
    }
}