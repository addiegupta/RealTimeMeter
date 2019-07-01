package com.server.provision


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import akka.stream.ActorMaterializer
import com.server.provision.MediatorActor.UpdateBalance
import com.server.provision.MeteringActor.EndCallMeter

import scala.concurrent.duration._

object MeteringActor
{
  def props(id: Int,balance:Int)(implicit materializer: ActorMaterializer, system: ActorSystem)  =
    Props(classOf[MeteringActor],id,balance,materializer,system)
  case object EndCallMeter

}

class MeteringActor(id: Int, var balance:Int) (implicit materializer: ActorMaterializer, system: ActorSystem) extends Actor
  with ActorLogging{
  var cancellable:Cancellable = null
  implicit val ec = system.dispatcher
  override def preStart(): Unit = {
    log.info(s"Started Balance meter for id:$id with balance: $balance")

    cancellable = context.system.scheduler.schedule(0 seconds, 1 seconds) {

      balance-=1
      log.info(s"ID: $id ; Balance: $balance")
      if(balance==0){
        cancellable.cancel()
        context.parent!UpdateBalance(id,balance)
        context stop self
      }

    }
    //This cancels further Ticks to be sent

  }
  override def receive: Receive = {
    case EndCallMeter=>
      log.info(s"Call Ended for id: $id")
      cancellable.cancel()
      sender()!UpdateBalance(id,balance)
      context stop self
    //      val planDataActor: ActorRef = context.actorOf(PlanDataActor.props, "planDataActor")
    //
    //      planDataActor ! UpdateBalanceById(id,balance-10)

  }
  override def postStop(): Unit = {
    log.info(s"Stopped Balance meter for id: $id")
  }
}
