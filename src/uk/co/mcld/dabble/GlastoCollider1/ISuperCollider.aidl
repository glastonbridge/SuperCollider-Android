package uk.co.mcld.dabble.GlastoCollider1;
/** Provide IPC to the SuperCollider service.
 *
 */
interface ISuperCollider {
    // Kick off the run loop, if not running.  SuperCollider is processor-intensive so try
    // not to run it if you don't require audio  
	void start();
	// Terminate the run loop, if running.
	void stop();
}