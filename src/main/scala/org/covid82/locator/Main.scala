package org.covid82.locator

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.syntax.functor._
import cats.effect.{Sync, Resource}
import natchez.EntryPoint
import natchez.jaeger.Jaeger
import io.jaegertracing.Configuration.{SamplerConfiguration,ReporterConfiguration}
import fs2.concurrent.SignallingRef

object Main extends IOApp {

  def entryPoint[F[_] : Sync]: Resource[F, EntryPoint[F]] = Jaeger.entryPoint[F]("locator") { conf =>
    Sync[F].delay(conf
      .withSampler(SamplerConfiguration.fromEnv())
      .withReporter(ReporterConfiguration.fromEnv())
      .getTracer
    )
  }

  import scala.concurrent.duration._
  import scala.util.Try

  val config: DbConfig = DbConfig(
    url    = sys.env.getOrElse("DB_SERVICE", "jdbc:postgresql://localhost:5432/registry?protocolVersion=3&stringtype=unspecified&socketTimeout=300&tcpKeepAlive=true"),
    driver = sys.env.getOrElse("DB_DRIVER", "org.postgresql.Driver"),
    user   = sys.env.getOrElse("DB_USER", "postgres"),
    pass   = sys.env.getOrElse("DB_PASS", "postgres"),
    delay  = Try(Duration(sys.env.getOrElse("LOAD_FREQUENCY", "5minutes")))
      .map(duration => FiniteDuration(duration.length, duration.unit))
      .getOrElse(1.day)
  )

  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO].use { implicit blocker =>
      entryPoint[IO].use { implicit entryPoint =>
        SignallingRef[IO, Option[IpRegistry]](Option.empty).flatMap { registryRef =>
          AppServer
            .stream[IO](RegistryReader.db[IO](config), registryRef)
            .compile
            .drain
            .as(ExitCode.Success)
        }
      }
    }
}