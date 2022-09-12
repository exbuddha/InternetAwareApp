# InternetAwareApp


This sample code demonstrates an Android application that lazily updates the network connectivity status from the device
and a remote server on a background IO thread. A simple probing loop is launched every time the activity is resumed.
The delay times can be adjusted to control intervals for waking up and for testing the remote server. There are
two separate network-related sources of information. One is provided by the system's default network capabilities
callback, and the other is from probing the remote server connection. There is a hard limit of 3 seconds for running the tests.

One challenge that the code neatly solves is to postpone work to a later time until a session id is acquired from the database.
In order to achieve this while keeping the design restriction to never block the main thread in mind, a runner
is introduced that can schedule code to run in a context concurrent to the application in a few simple steps:

1. Declare the async work as a function returning a live data object that is assigned to run in a background context:
  
       private fun runAsync() = liveData(Dispatchers.IO) {
           try {
               // ... perform blocking task
           }
           catch (ex: Throwable) {
               if (ex is CancellationException)
                   throw ex
           }
           emit(result)
       }

2. Attach the task to the application runner:

       attach(::runAsync) { result ->
           // ... capture result on main thread
       }

3. Start or resume the runner:

       start()    // restarts all steps
       resume()   // continues from last executed step (useful in case of errors)

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

   The runner may need to be resumed again if it has completed by this time.
