import java.security.MessageDigest
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.PoisonPill
import akka.actor._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

object project1 {
	var count: Int = _
	def main(args: Array[String]){

		case class SearchBitcoins()
		case class StartWork(startrange: Int, endrange:Int)
		case class WorkDone(list:StringBuilder)
		case class Consolidate(list: StringBuilder, remoteCount: Int)
		case class RemoteWorkDone(list: StringBuilder)
		case class PrintBitcoins(masterList: StringBuilder)
		case class ShutDown(masterList: StringBuilder, ipAddress: String)
		case class AskMaster()
		case class StartMining(targetZeroes: Int)
		case class SendTargetZeros()
		
		// Three type of actors - Master, RemoteMaster, Worker
		class Master(nrOfWorkers: Int, targetZeroes: Int)
			extends Actor{

				var masterList = new StringBuilder("": String)
				var inc: Int = _
				val starttime: Long = System.currentTimeMillis
				val worksize: Int = 1000000
			def receive = {
				case SearchBitcoins() => 
					println("spwaning workers")
					for(i <- 0 until nrOfWorkers) {
						val act = context.actorOf(Props(new Worker(targetZeroes))) ! StartWork(inc*worksize,(inc+1)*worksize)
						// actorList += act
						inc = inc+1
					}

				case WorkDone(list: StringBuilder) =>
					print("..")
					masterList.append(list)
					inc = inc+1
					sender ! StartWork((inc-1)*worksize,(inc)*worksize)

				case SendTargetZeros() =>
					sender ! StartMining(targetZeroes)

				case Consolidate(list: StringBuilder, remoteCount: Int) =>
					count += remoteCount
					masterList.append(list)
					println("Got " + remoteCount + " BitCoins from remote master")
					
				case "STOP" =>
					println(masterList)
					println("\n========== Toal BitCoins : " + count + " ==========")
					context.system.shutdown()
					
			}
		}

		class RemoteMaster(nrOfWorkers: Int, ipAddress: String) 
			extends Actor{

			var masterList = new StringBuilder("": String)
			var inc: Int = _
			val starttime: Long = System.currentTimeMillis
			val worksize: Int = 1000000
			val masterActor = context.actorFor("akka.tcp://MasterSystem@" + ipAddress + ":2552/user/master")

			def receive = {
				case AskMaster() =>
					println("Ask master for target zeros")
					masterActor ! SendTargetZeros()

				case StartMining(targetZeroes: Int) => 
					println("start mining")
					for(i <- 0 until nrOfWorkers) {
						val act = context.actorOf(Props(new Worker(targetZeroes))) ! StartWork(inc*worksize,(inc+1)*worksize)
						// actorList += act
						inc = inc+1
					}
				case WorkDone(list: StringBuilder) =>
					print("..")
					masterList.append(list)
					inc = inc+1
					sender ! StartWork((inc-1)*worksize,(inc)*worksize)

				case "REMOTE_STOP" =>
					val masterActor = context.actorFor("akka.tcp://MasterSystem@" + ipAddress + ":2552/user/master")
					masterActor ! Consolidate(masterList,count)
					context.system.shutdown()
			}

		}

		class Worker(targetzeroes:Int) extends Actor{
			def receive = {
				case StartWork(startrange: Int, endrange:Int) =>
					var list = new StringBuilder("": String)
					for (i <- startrange until endrange){
						var str = "sdarsha".concat(Integer.toString(i,36))
						var crypted = sha256(str)
						if(hasZeroes(crypted,targetzeroes)){
							count = count + 1;
							list.append(str + " : " + crypted + "\n")
						}
					}
					sender ! WorkDone(list)

			}
		}

		def sha256(s: String): String = {
			val md = MessageDigest.getInstance("SHA-256")
			val digest: Array[Byte] = md.digest(s.getBytes)
			var sb: StringBuffer = new StringBuffer
			digest.foreach { digest =>
				var hex = Integer.toHexString(digest & 0xff)
				if (hex.length == 1) sb.append('0') 
				sb.append(hex)
			}
			sb.toString()
		}

		def hasZeroes(s: String, targetzeroes:Int): Boolean = {
			var count: Int = targetzeroes
			var found : Boolean= true;
			while(count>0 && found){
				if(s.charAt(targetzeroes-count) == '0'){
					count = count-1;
				} else{
					found = false
				}
			}
			found
		}

		 val MasterConfig = ConfigFactory.parseString("""akka{
				actor{
					provider = "akka.remote.RemoteActorRefProvider"
				}
				remote{
					enabled-transports = ["akka.remote.netty.tcp"]
					netty.tcp {
						hostname = "192.168.2.3"
						port = 2552
					}
				}
			}""")
		val RemoteMasterConfig = ConfigFactory.parseString("""akka{
				actor{
					provider = "akka.remote.RemoteActorRefProvider"
				}
				remote{
					enabled-transports = ["akka.remote.netty.tcp"]
					netty.tcp{
						port = 0
					}
				}
			}""")

		if(args(0).contains('.')) {
			val nrOfWorkers: Int = Runtime.getRuntime().availableProcessors()
			val system = ActorSystem("RemoteMasterSystem", ConfigFactory.load(RemoteMasterConfig))
			// val listener = system.actorOf(Props[Listener], name = "listener")
			val remotemaster = system.actorOf(Props(new RemoteMaster(nrOfWorkers,args(0))), name = "remotemaster")
			
			import system.dispatcher
			system.scheduler.scheduleOnce(300000 milliseconds, remotemaster, "REMOTE_STOP")
			remotemaster ! AskMaster()
		} else {
			val nrOfWorkers: Int = Runtime.getRuntime().availableProcessors()
			val system = ActorSystem("MasterSystem", ConfigFactory.load(MasterConfig))
			// val listener = system.actorOf(Props[Listener], name = "listener")
			val master = system.actorOf(Props(new Master(nrOfWorkers,args(0).toInt)), name = "master")
			// val master = system.actorOf(Props(new Master(4,4, listener), name = "master"))
			//Scheduler to give STOP command after 2 min
			import system.dispatcher
			system.scheduler.scheduleOnce(180000 milliseconds, master, "STOP")
			println("Starting Master")
			master ! SearchBitcoins()
		}
	}	
}