package app

import com.redis._

object Cache {
  private val REDIS_HOST = sys.env.getOrElse("REDIS_HOST", "localhost")
  private val REDIS_PORT: Int = sys.env.getOrElse("REDIS_PORT", "6379").toInt

  def redisClient = new RedisClient(REDIS_HOST, REDIS_PORT)
}
