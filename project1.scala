import java.security.MessageDigest
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.remote.RemoteActorRefProvider

object project1 {
	def main(args: Array[String]){
		println(sha256("sarojini!!!!!HAVHgogfflalfbalffFIFfGAIS"))
		case class SearchBitcoins()
		case class Result()
		case class StartWork()
		
		
		// Four type of actors - Master, RemoteMaster, Worker, Listener
		class Master(nrOfWorkers: Int, targetZeroes: Int, listener: ActorRef)
			extends Actor{

				var inc: Int = _
				val starttime: Long = System.currentTimeMillis
				val worksize: Int = 1000000
			def receive = {
				case SearchBitcoins() => 
					for(i <- 0 until nrOfWorkers) {
						context.actorOf(Props(new Worker(targetZeroes,inc*worksize,(inc+1)*worksize))) ! StartWork()
						inc = inc+1;
					}

			}
		}

		// class RemoteMaster(nrOfWorkers: Int, ipAddress: String, listener: ActorRef) 
		// 	extends Actor{

		// 	def receive = {
				
		// 	}
		// }

		class Worker(targetzeroes:Int, startrange: Int, endrange:Int) extends Actor{
			def receive = {
				case StartWork() =>
					println("Started work")
					// var flag = true
					// val attempts = startrange
					// while(flag){
					// 	var str = getNewString(attempts)
					// }

			}
		}

		class Listener extends Actor{
			def receive = {
				case Result() => 
					println("result!!!")
			}
		}

		 val MasterConfig = ConfigFactory.parseString("""akka{
				actor{
					provider = "akka.remote.RemoteActorRefProvider"
				}
				remote{
					enabled-transports = ["akka.remote.netty.tcp"]
					netty.tcp {
						hostname = "127.0.0.1"
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

		// if(args(0).contains('.')) {
		// 	val nrOfWorkers: Int = Runtime.getRuntime().availableProcessors()
		// 	val system = ActorSystem("RemoteMasterSystem", ConfigFactory.load(RemoteMasterConfig))
		// 	val listener = system.actorOf(Props[Listener], name = "listener")
		// 	val remotemaster = system.actorOf(Props(new RemoteMaster(nrOfWorkers,args(0), listener), name = "remotemaster"))
		// } else {
			val nrOfWorkers: Int = Runtime.getRuntime().availableProcessors()
			val system = ActorSystem("MasterSystem", ConfigFactory.load(MasterConfig))
			val listener = system.actorOf(Props[Listener], name = "listener")
			val master = system.actorOf(Props(new Master(nrOfWorkers,args(0).toInt, listener)), name = "master")
			// val master = system.actorOf(Props(new Master(4,4, listener), name = "master"))
			master ! SearchBitcoins()
		// }
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

	def getNewString(numcnt: Int): String = {
		val gid = "sdarsha"
		var strcnt: BigInt = numcnt
		var stringofa = strcnt.toString(36)
		var coin = gid.concat(stringofa)
		coin
	}
}