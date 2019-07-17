
package com.callmeter.system

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.callmeter.system.DbActor.{FindPlanById, UpdateBalanceById}
import com.callmeter.system.MeteringActor.{DeliberateFailMeter, EndCallMeter, Greetings}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Companion object for the MediatorActor class
  * Contains the messages that this Actor receives and the props method that is used to construct a
  * MediatorActor instance passing in a reference to the database pooling actor and the implicit ActorSystem instance
  */
object MediatorActor{
  def props(dbActorPool: ActorRef)(implicit system : ActorSystem) =
    Props(classOf[MediatorActor],dbActorPool,system)

  case object EndCallMediator
  case class StartCall(id:Int)
  case class UpdateBalance(id:Int,balance:Int)
  case class ReceivePlanDetails(f:Future[Option[Int]],id:Int)
  case object InitiateDeliberateMeterCrash
}

/**
  * Acts as a mediator between MeteringActor, WebServer and the DB Pooling Actor
  */
class MediatorActor(dbActorPool: ActorRef)(implicit system: ActorSystem) extends Actor
with ActorLogging{
  import MediatorActor._

  implicit val ec = system.dispatcher

  // Reference to Metering Actor, null initiated
  var meterActor:ActorRef=null

  // Id of the Caller saved for logging purposes
  var id :Int= 0

  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._

  // Supervisor Strategy implementation to be executed in case a child actor fails
  override val supervisorStrategy =
    OneForOneStrategy() {
      case _: ArithmeticException      => log.info(s"Resuming Meter Actor for id: #$id#")
                                          Resume
      case _: NullPointerException     => log.info(s"Restarting Meter Actor for id: #$id#")
                                          Restart
      case _: IllegalArgumentException => log.info(s"Stopping Meter Actor for id: #$id#")
                                          Stop
      case _: Exception                =>log.info(s"Resuming Meter Actor for id: #$id#")
                                          Resume
    }

  // Log before starting actor instance
  override def preStart(): Unit = {
    log.info("Started new MediatorActor")
  }

  // Log after stopping actor instance
  override def postStop(): Unit = {
    log.info("Stopped MediatorActor")
  }

  // Handling of messages received by this actor
  override def receive: Receive = {

    // Start the metering of call for user of given id; sent from web server
    case StartCall(id)=>

      log.info(s"Starting call for id: #$id# inside mediator")
      this.id = id

      // Fetch plan details of user from DB Pooling Actor
      dbActorPool ! FindPlanById(id)

    // Crash the error deliberately; called from web server
    case InitiateDeliberateMeterCrash => meterActor!DeliberateFailMeter

    // End the call; sent from web server
    // Update balance of user in db; sent by MeteringActor
    case EndCallMediator=>

        log.info(s"Ending call - Mediator to BalanceMeterActor for id: #$id#")

        // Instruct metering Actor to end call
        meterActor ! EndCallMeter

    // Receive initial plan details of user from one of the DB access actors managed by DB Pooling Actor
    // Data is enclosed in a Future: i.e. the data becomes available at a later point in time
    case UpdateBalance(id,balance)=>

      // Send update request to DB Pooling actor
      dbActorPool ! UpdateBalanceById(id,balance)

      // All tasks of this mediator completed; stop self and free up resources
      context stop self

    case ReceivePlanDetails(f,id)=>

      log.info(s"Waiting for Plan Details for id: #$id#")

      // Handling the data from the future
      f.onComplete{

        // Data successfully received
        case Success(v)=>

          val balance:Int = v match{
            case Some(s) => s

            // If None is received (some error has occurred in retrieving balance), then init balance to -1
            case None => -1
          }

          // No balance; call cannot be started, hence stop self
          if(balance==0)
          {
            log.info(s"Balance 0 for id: #$id# , Call Cannot be established")
            context stop self
          }

          // Error retrieving plan details, user does not exist in database, stop self
          else if(balance<0)
          {
            log.info(s"Record Not Found for #$id#")//Success with None
            context stop self
          }

          // Start the metering actor
          else {

            log.info(s"Found Valid Plan Details for id: #$id# , Call can be established. Hence, Creating Metering Actor.")

            meterActor = context.actorOf(MeteringActor.props(id, balance), name = s"balanceMeterActor-$id")

            // Greeting sent so that visualmailbox can display the meter actor
            // Does not serve any purpose relevant to application, is just present to display flow of message in visualmailbox
            meterActor ! Greetings
          }

        // Some Failure occurred in the received Future, log the exception
        case Failure(exception)=>log.info(s"FAILURE: $exception")
      }
  }
}
