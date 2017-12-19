# Web crawler
A simple web crawler built with Akka and Akka Streams. 
It uses the websocket protocol to provide better interactivity.

## Goal
The crawler should be limited to one domain - so when
crawling tomblomfield.com it would crawl all pages 
within the domain, but not follow external links, 
for example to the Facebook and Twitter accounts. 
Given a URL, it should output a site map, showing which
static assets each page depends on, and the links between pages.


## Run it locally
You'll need to have Scala and SBT (Simple Build Tool) installed in order to run the project.
It also needs to have a Redis server running locally, 
listening on port 6379 unless specified otherwise.

Once installed, run:
```
sbt run
```

## Considerations
Crawling a website can take a long time. 
Seeing a loader for many minutes without any feedback didn't seem like a good idea.
Thus I considered that interactivity was important.
Every page in a domain that is crawled is sent to the user immediately.
When crawling is finished, the server sends a successful event.

The user is able to stop and request to crawl a new url. 
Each request will trigger a new worker (actor) that will crawl that specific url.

The Akka toolkit provides an abstraction to architect your program following the actor model.
Actors are single threaded. They communicate with each other via message passing.
One actor sends messages to another actor's mailbox and 
that target actor will only read the message when its current message is processed.
In our case, it means that a worker will perform its crawling until successfully finished
or until it encounters an error, but that computation cannot be interrupted by
another actor's message. 
Once they finish their computation, they will cache their results in an in-memory database.
We use Redis for that matter.

In case a user stops and starts crawling a domain that is already being crawled,
the Akka supervisor will not create a new worker to run the same computation but
will start listening to messages from the existing worker again.

## Run tests
Run `sbt test`

It focuses on:
- *Interactivity*: A user that requests a valid url gets its sitemap eventually while 
receiving each crawled page in the meantime. 
- *Start/stop crawling*: It makes sure that there is 
at most one worker sending messages to the client, that
there are never two workers running the same computation at the same time, etc
- *Caching*: It doesn't compute the same expensive computation twice

No mocking was done to query urls, so internet is required to pass these tests.
A redis server is also required to be running locally.