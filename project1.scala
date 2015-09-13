import java.security.MessageDigest
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

object project1 {
	var count: Int = _   // This is used to store total number of bitcons found
	def main(args: Array[String]){

		case class SearchBitcoins() // this is the starting point for master and spawns workers
		case class StartWork(startrange: Int, endrange:Int) // starting point for worker actor
		case class WorkDone(list:StringBuilder) // this message is sent to master by worker with list of bitcoins found
		case class Consolidate(list: StringBuilder, remoteCount: Int) // message from remote master to merge bitcoins
		case class PrintBitcoins(masterList: StringBuilder) // print bitcoins found to console
		case class AskMaster() // message to master asking for target zeros from remote master
		case class StartMining(targetZeroes: Int) // start mining on remote master
		case class SendTargetZeros() // tell remote master about the number of target zeros
		
		// Three type of actors - Master, RemoteMaster, Worker
		class Master(nrOfWorkers: Int, targetZeroes: Int)
			extends Actor{

				var masterList = new StringBuilder("": String)
				var inc: Int = _
				val starttime: Long = System.currentTimeMillis
				val worksize: Int = 1000000
			def receive = {
				case SearchBitcoins() => 
					println("No of workers spawned is " + nrOfWorkers)
					for(i <- 0 until nrOfWorkers) {
						val act = context.actorOf(Props(new Worker(targetZeroes))) ! StartWork(inc*worksize,(inc+1)*worksize)
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
					// val programtime: Long = (System.currentTimeMillis - starttime).millis
					println("\n************ Total BitCoins Found : " + count + " ***************")
					println("************ Mining time on this device : " + (System.currentTimeMillis - starttime).millis + " ***************")
					context.system.shutdown()
					
			}
		}

		class RemoteMaster(nrOfWorkers: Int, ipAddress: String) 
			extends Actor{

			var masterList = new StringBuilder("": String)
			var inc: Int = _
			val starttime: Long = System.currentTimeMillis
			val worksize: Int = 20000
			val masterActor = context.actorFor("akka.tcp://MasterSystem@" + ipAddress + ":2552/user/master")

			def receive = {
				case AskMaster() =>
					println("Ask master for target zeros")
					masterActor ! SendTargetZeros()

				case StartMining(targetZeroes: Int) => 
					println("No of workers spawned is " + nrOfWorkers)
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

				case "STOP" =>
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
						hostname = "192.168.0.111"
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
			val remotemaster = system.actorOf(Props(new RemoteMaster(nrOfWorkers,args(0))), name = "remotemaster")
			//Scheduler to give STOP command after 2 min
			import system.dispatcher
			system.scheduler.scheduleOnce(60000 milliseconds, remotemaster, "STOP")
			remotemaster ! AskMaster()
		} else {
			val nrOfWorkers: Int = Runtime.getRuntime().availableProcessors()
			val system = ActorSystem("MasterSystem", ConfigFactory.load(MasterConfig))
			val master = system.actorOf(Props(new Master(nrOfWorkers,args(0).toInt)), name = "master")
			//Scheduler to give STOP command after 3 min
			import system.dispatcher
			system.scheduler.scheduleOnce(600000 milliseconds, master, "STOP")
			println("Starting Master")
			master ! SearchBitcoins()
		}
	}	
}