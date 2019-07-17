package com.server.provision


import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props}
import com.server.provision.MediatorActor.UpdateBalance
import com.server.provision.MeteringActor.{DeliberateFailMeter, EndCallMeter, Greetings}

import scala.concurrent.duration._

/**
  * Companion object for the MeteringActor class
  * Contains the messages that this Actor receives and the props method that is used to construct a
  * MeteringActor instance, passing in the implicit ActorSystem, id and initial balance of user
  */
object MeteringActor
{
  def props(id: Int,balance:Int)(implicit system: ActorSystem)  =
    Props(classOf[MeteringActor],id,balance,system)
  case object EndCallMeter
  case object Greetings
  case object DeliberateFailMeter
}

/**
  * Keeps track of the balance of the user during the call
  */
class MeteringActor(id: Int, var balance:Int) (implicit system: ActorSystem) extends Actor
  with ActorLogging{

  implicit val ec = system.dispatcher
  var cancellable:Cancellable = null

  // Start metering of the call
  override def preStart(): Unit = {

    // Issue low balance warning as a log if balance is less than 100 units
    if(balance<100)
      log.warning(s"Low Balance for id: #$id# , Please Top Up balance soon")

    log.info(s"Started Balance meter for id:#$id# with balance: $balance")

    // Decrease balance by 1 unit every second in the scheduler
    cancellable = context.system.scheduler.schedule(0 seconds, 1 seconds) {

      balance-=1
      log.info(s"ID: #$id# ; Balance: $balance")

      // Low balance warning
      if(balance==100){
        log.warning(s"Low Balance ,Call will disconnect in 100 seconds")
      }

      // Balance finished=> stop metering, send updated balance to mediator and stop self
      if(balance==0){
        cancellable.cancel()
        context.parent!UpdateBalance(id,balance)
        context stop self
      }
    }
  }

  // Message handling
  override def receive: Receive = {

    // Deliberately crash the actor; sent by MediatorActor
    case DeliberateFailMeter =>
      throw new Exception("Deliberate Failure in Metering Actor")

    // Stop metering of call; sent by MediatorActor
    case EndCallMeter=>
      log.info(s"Call Ended for id: #$id#")
      cancellable.cancel()
      sender()!UpdateBalance(id,balance)
      context stop self

    // First message sent by Mediator; this message serves no purpose to the application
    // it is only present to display a message flow in the akka-visualmailbox view
    case Greetings =>
      log.info("Received greetings from mediator in meter")
  }

  override def postStop(): Unit = {
    log.info(s"Stopped Balance meter for id: #$id#")
    cancellable.cancel()
  }
}
