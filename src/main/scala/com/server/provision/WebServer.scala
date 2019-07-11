package com.server.provision

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.util.Timeout
import MediatorActor._
import akka.routing.FromConfig
import spray.routing.SimpleRoutingApp

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object WebServer extends SimpleRoutingApp {

  implicit val system = ActorSystem("RealTimeMeterSystem")
  implicit def executor: ExecutionContext = system.dispatcher
  protected val log = Logging(system.eventStream, "RealTimeMeter-log")

  log.info("Starting planDbActor")
  val dbActorPool = system.actorOf(FromConfig.props(PlanDbActor.props), "random-router-pool-resizer")


  def main(args: Array[String]): Unit = {

    startServer(interface = "localhost", port = 8181) {
      get {
        pathSingleSlash {
          complete("RealTime Metering System is up and running!")
        } ~
          path("start-call") {
            parameters("id") { id =>
              try {
                val mediatorActor = system.actorOf(MediatorActor.props(dbActorPool), s"mediator-$id")
                log.info(s"Created mediator actor for id #$id# and now initiating meter")
                mediatorActor ! InitiateMeter(Integer.valueOf(id))
                complete(s" Starting call for id $id")
              } catch {
                case ex: akka.actor.InvalidActorNameException =>
                  log.info(s"Already in call for id #$id# , Exception caught $ex ")
                  complete(s"Cannot Start, Already in call for id $id")
              }
            }
          } ~
          path("stop-call") {
            parameters("id") { id =>
              val start = System.currentTimeMillis()
              implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
              onComplete(system.actorSelection("user/" + s"mediator-$id").resolveOne()) {
                case Success(actorRef) => // logic with the actorRef
                  log.info(s"Stopping call for id: $id")
                  actorRef ! EndCallMediator
                  complete(s"Call stopped for id: $id in ${(System.currentTimeMillis() - start) / 1000} seconds")
                case Failure(ex) =>
                  log.warning(s"mediatorActor $id does not exist $ex")
                  complete(s"Unable to stop call for id: $id \nError: $ex")
              }
            }
          } ~
          path("crash-meter-actor") {
            parameters("id") { id =>
              val start = System.currentTimeMillis()
              implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
              onComplete(system.actorSelection("user/" + s"mediator-$id").resolveOne()) {
                case Success(actorRef) => // logic with the actorRef
                  log.info(s"Crashing meter for id: #$id#")
                  actorRef ! "crash-meter"
                  complete(s"Crashed Meter Actor for id: #$id# in ${(System.currentTimeMillis() - start) / 1000} seconds")
                case Failure(ex) =>
                  log.warning(s"mediatorActor #$id# does not exist $ex")
                  complete(s"Unable to crash for id: $id \nError: $ex")
              }
            }
          }
      }
    }
  }
}
