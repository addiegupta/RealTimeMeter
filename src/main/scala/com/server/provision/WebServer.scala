package com.server.provision

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import MediatorActor._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object WebServer {

  implicit val system = ActorSystem("RealTimeMeterSystem")
  implicit def executor: ExecutionContext = system.dispatcher
  protected val log = Logging(system.eventStream, "RealTimeMeter-log")
  protected implicit val materializer: ActorMaterializer = ActorMaterializer()

  def main(args: Array[String]): Unit = {
    val endpoint = "ws://127.0.0.1:8080"

    val provisioningRoute =
      get{
        path("start-call"/ IntNumber){ id =>
            val mediatorActor = system.actorOf(MediatorActor.props,s"mediator-$id")
            log.info(s"Created mediator actor for id $id and now initiating meter")
            mediatorActor ! InitiateMeter(id)
            complete(s" Starting call for id $id")
        } ~
        path("stop-call" / IntNumber){ id =>

            //TODO Find way to display error if any
          implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
          val mediatorActor = system.actorSelection("user/" + s"mediator-$id").resolveOne().onComplete {
            case Success(actorRef) => // logic with the actorRef
              log.info(s"Stopping call for $id")
              actorRef ! EndCallMediator
            case Failure(ex) =>
              log.warning(s"mediatorActor $id does not exist $ex")
          }
            complete(s"Stopping call for id $id")
        }
      }


    val httpInterface = "127.0.0.1"
    val httpPort = 8080

    log.info(s"About to bind to: $httpInterface and: $httpPort")
    val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(provisioningRoute, httpInterface, httpPort)

    bindingFuture.map { serverBinding =>
      log.info(s"Bound to: ${serverBinding.localAddress} ")
    }.onComplete {
      case Success(value) => log.info("RealTimeMeter WebServer started successfully")
      case Failure(ex) =>
        log.error(ex, "Failed to bind to {}:{}!", httpInterface, httpPort)
        Http().shutdownAllConnectionPools()
        system.terminate()
    }

    scala.sys.addShutdownHook {
      log.info("Terminating...")
      Http().shutdownAllConnectionPools()
      system.terminate()
      Await.result(system.whenTerminated, 30.seconds)
      log.info("Terminated... Bye")
    }

  }
}
