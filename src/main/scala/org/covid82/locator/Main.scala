package org.covid82.locator

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.syntax.functor._
import cats.effect.{Resource, Sync}
import eu.timepit.refined.types.string.TrimmedString
import fs2.aws.sqs.{SQSConsumerBuilder, SqsConfig}
import fs2.aws.sqsStream
import natchez.EntryPoint
import natchez.jaeger.Jaeger
import io.jaegertracing.Configuration.{ReporterConfiguration, SamplerConfiguration}
import fs2.concurrent.SignallingRef
import javax.jms.{Message, TextMessage}

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

  val sqsConfig: SqsConfig = SqsConfig(TrimmedString.trim(sys.env.getOrElse("NOTIFICATION_QUEUE", "registry-notification")))

  implicit val messageDecoder: Message => Either[Throwable, RegistryUpdated] = { sqs_msg =>
    import io.circe.generic.auto._
    io.circe.parser.decode[RegistryUpdated](sqs_msg.asInstanceOf[TextMessage].getText)
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val registryReader = RegistryReader.db[IO](config)
    val watcher = sqsStream[IO, RegistryUpdated](sqsConfig, SQSConsumerBuilder(_, _))
    Blocker[IO].use { implicit blocker =>
      entryPoint[IO].use { implicit entryPoint =>
        SignallingRef[IO, Option[IpRegistry]](Option.empty).flatMap { registryRef =>
          AppServer
            .stream[IO](registryReader, registryRef, watcher)
            .compile
            .drain
            .as(ExitCode.Success)
        }
      }
    }
  }
}