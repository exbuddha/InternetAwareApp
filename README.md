# InternetAwareApp

This sample code demonstrates an Android application that updates the network connectivity status from the device
and a remote server on a background IO thread. A simple probing loop is launched every time the activity is resumed,
with adjustable delay times to control intervals for waking up and for testing the remote server. There are
two separate network-related sources of information. One is provided by the system's default network capabilities
callback, and the other is from testing the remote server connection. There is a hard limit of 3 seconds for running the test.

One challenge that the code solves is to postpone work to a later time until a session id is acquired from the database.
In order to achieve this while keeping the design restriction to never block the main thread in mind, a runner
is introduced that can schedule code to run in a context concurrent to the application in a few easy steps:

1. Declare the async work as a function accepting a live data scope that will be used in a background context:
  
       suspend fun runAsync(scope: LiveDataScope<Any?>) { scope.apply {
           try {
               // ... perform blocking task
           }
           catch (ex: Throwable) {
               if (ex is CancellationException)
                   throw ex
           }
           emit(result)
       } }

2. Attach the work to the application runner:

       attach(Dispatchers.IO, ::runAsync) { result ->
           // ... capture result on main thread
       }

3. Start or resume the runner:

       start()    // restarts all steps
       resume()   // continues from next step
       retry()    // continues from last step (useful for error cases)

4. If there is work that may be started from a background thread while the runner is active simultaneously, such as a
   callback on the connectivity thread, then that work must also be scheduled to run along the main thread:

       attach(Dispatchers.Unconfined, {
           // ... async work that runs along main thread
           // ... you can attach new work to start immediately after this work finishes
           emit(result)
       }) {
           // ... capture result on main thread
       }

       capture {
           // ... sync work that runs along main thread (null result)
       }

   The runner may need to be resumed again if it has completed by this time. This must only be done inside a capture
   block or from the main thread.
