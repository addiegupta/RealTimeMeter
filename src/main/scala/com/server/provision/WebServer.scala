package com.server.provision

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import MediatorActor._
import akka.routing.FromConfig

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * The Web Server that creates an API for user interaction
  * - Creates an ActorSystem in which all the actors get created
  * - Starts and Stops calls on receiving HTTP requests
  * - Provides API to simulate crash of meter running for a particular call
  */
object WebServer {

  // Actor System that contains all actors
  implicit val system = ActorSystem("RealTimeMeterSystem")

  // Passed as an implicit value for executing certain functions
  implicit def executor: ExecutionContext = system.dispatcher

  // Logger
  protected val log = Logging(system.eventStream, "RealTimeMeter-log")

  // Materializes streams for running the server
  protected implicit val materializer: ActorMaterializer = ActorMaterializer()


  def main(args: Array[String]): Unit = {

    log.info("Starting Plan Database Router Pooler")
    val dbActorPool:ActorRef = system.actorOf(FromConfig.props(DbActor.props),"random-router-pool-resizer")

    // The HTTP Route for running the provisioning server
    val provisioningRoute =
      get{
        /**
          * 1. / : Useful for checking if server is running or not
          */
        pathSingleSlash{
          complete("RealTime Metering System is up and running!")
        } ~
          /**
            * 2. /start-call?id=<id> : Starts the metering of call for user with id = <id>
            *   Creates a mediator actor to handle the call and sends it a message to start metering
            */
        path("start-call"){
          parameters("id"){ id =>
            try{

              // Start mediator actor and send message to start call
              val mediatorActor = system.actorOf(MediatorActor.props(dbActorPool),s"mediator-$id")
              log.info(s"Created mediator actor for id #$id# and Initiating meter for Caller")
              mediatorActor ! StartCall(Integer.valueOf(id))

              // HTTP Response text
              complete(s" Starting call for id $id")

            }catch {

              // Mediator is already present for particular id => call is already running
              case ex: akka.actor.InvalidActorNameException =>
                log.info(s"Already in call for id #$id# , Exception caught $ex ")
                complete(s"Cannot Start, Already in call for id $id")
            }
          }
        } ~
          /**
            * 3. /stop-call?id=<id>: Stops the metering of call for user with id = <id>
            *   Finds the mediator actor for this user in the actor system and
            *   sends it a message to stop the call
            */
          path("stop-call" ){
            parameters("id") { id =>

              // Allow a maximum timeout of 1 second to find the mediator actor in the actor system
              implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))

              // The mediator actor is named as: mediator-<id> hence it can be searched for using id
              onComplete(system.actorSelection("user/" + s"mediator-$id").resolveOne()) {

                // Mediator Actor found, send message to stop call
                case Success(actorRef) =>

                  log.info(s"Stopping call for id: $id")
                  actorRef ! EndCallMediator
                  complete(s"Call stopped for id: $id ")

                // Mediator Actor not found, call is not running hence cannot be stopped or some other error
                case Failure(ex) =>
                  log.warning(s"mediatorActor $id does not exist $ex")
                  complete(s"Unable to stop call for id: $id \nError: $ex")
              }
            }
          }~
          /**
            * 4. /crash-meter-actor?id=<id> : Crashes the meter actor for user with id = <id>
            *   This API is used for testing the self healing mechanism of the Actor System
            *   On crashing the actor, it should resume operation from its last known point
            */
          path("crash-meter-actor" ){
            parameters("id") { id =>

              // Allow max timeout of 1 second to find mediator
              implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))

              onComplete(system.actorSelection("user/" + s"mediator-$id").resolveOne()) {
                case Success(actorRef) =>

                  // Send message to crash the metering actor and return http response
                  log.info(s"Crashing meter for id: #$id#")
                  actorRef ! InitiateDeliberateMeterCrash
                  complete(s"Crashed Meter Actor for id: #$id# ")

                // Call not running or some other error
                case Failure(ex) =>
                  log.warning(s"mediatorActor #$id# does not exist $ex")
                  complete(s"Unable to crash for id: $id \nError: $ex")
              }
            }
          }
      }

    // HTTP Address to communicate with the server
    val httpInterface = "127.0.0.2"
    val httpPort = 8181

    // Bind the route to the address
    log.info(s"About to bind to: $httpInterface and: $httpPort")
    val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(provisioningRoute, httpInterface, httpPort)

    // Handle failures
    bindingFuture.map { serverBinding =>
      log.info(s"Bound to: ${serverBinding.localAddress} ")
    }.onComplete {
      case Success(value) => log.info("RealTimeMeter WebServer started successfully")
      case Failure(ex) =>
        log.error(ex, "Failed to bind to {}:{}!", httpInterface, httpPort)
        Http().shutdownAllConnectionPools()
        system.terminate()
    }

    // Error handling in case of system shutdown
    scala.sys.addShutdownHook {
      log.info("Terminating RealTimeSystem...")
      Http().shutdownAllConnectionPools()
      system.terminate()
      Await.result(system.whenTerminated, 30.seconds)
      log.info("Terminated RealTimeSystem")
    }
  }
}
