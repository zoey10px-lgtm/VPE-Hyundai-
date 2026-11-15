/** Separate authentication traffic from sensor/chat polling. Rejected requests
 * never extend the window, so repeated taps cannot prolong a lockout forever. */
export function createRateLimiter({windowMs=60000,maxKeys=1000}={}) {
 const attempts=new Map();
 return function rateLimited(ip,limit,bucket='api',now=Date.now()) {
  const key=bucket+':'+ip;
  const recent=(attempts.get(key)||[]).filter(t=>now-t<windowMs);
  const blocked=recent.length>=limit;
  if(!blocked)recent.push(now);
  attempts.set(key,recent);
  if(attempts.size>maxKeys){
   for(const [other,times]of attempts)if(!times.some(t=>now-t<windowMs))attempts.delete(other);
  }
  return blocked;
 };
}
