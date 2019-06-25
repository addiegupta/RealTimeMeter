package com.server.provision

import akka.Done
import akka.actor.{ActorRef, ActorSystem, InvalidActorNameException, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.Timeout
import play.api.libs.json.Json
import MediatorActor._

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object WebServer {

  implicit val system = ActorSystem("ServerSample2")
  implicit def executor: ExecutionContext = system.dispatcher
  protected val log = Logging(system.eventStream, "CallServer-main")
  protected implicit val materializer: ActorMaterializer = ActorMaterializer()


  object Messages  {


    def ack(aString: String) = TextMessage(Source.single("Ack from server: " + aString))
  }

  var callerActor: ActorRef = null
  def main(args: Array[String]): Unit = {
    val endpoint = "ws://127.0.0.1:8080"

    var idCount =0
    var mediatorActor:ActorRef = null
    val provisioningRoute =
      get{
        path("start-call"/ IntNumber){ id =>
            mediatorActor = system.actorOf(MediatorActor.props,s"mediator-$id")
            println(s"Created mediator actor for id $id and now initiating meter")
            mediatorActor ! InitiateMeter(id)
            complete(s" Starting call for id $id")
        } ~
        path("stop-call" / IntNumber){ id =>
            //find mediator actor using id
            println(s"Stopping call for id $id")
            // send stop message to mediator
            mediatorActor ! EndCallMediator
            complete(s" Stopping call for id $id")
        }
      }


    val httpInterface = "127.0.0.1"

    val httpPort = 8080


    log.info(s"About to bind to: $httpInterface and: $httpPort")
    val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(provisioningRoute, httpInterface, httpPort)


    bindingFuture.map { serverBinding =>
      log.info(s"Bound to: ${serverBinding.localAddress} ")
    }.onComplete {
      case Success(value) => log.info("CallServer started successfully")
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
