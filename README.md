# Customs ROSM Frontend

Frontend application for the Customs Register Once Subscribe Many (ROSM) service

## Feature Toggle 

Go to application.conf to enable or disable the below features

        features.rowHaveUtrEnabled=<Boolean>

## Development

You'll need [Service Manager](https://github.com/hmrc/service-manager) to develop locally.


#### Service Manager Commands

What's running?

    sm -s

Start the required development services (make sure your service-manager-config folder is up to date)

    sm --start CUSTOMS_ROSM_FRONTEND_ALL -f

Stop all running services

    sm --stop CUSTOMS_ROSM_FRONTEND_ALL

## Debugging

Before debugging locally you will need to stop the Service Manager-started Customs ROSM Frontend

    sm --stop CUSTOMS_ROSM_FRONTEND

And then start your local debugging session on the expected port

    sbt -jvm-debug 9999
    run 9830

    Or within an SBT Shell inside an IDE:
    run -Dlogger.customs-rosm-frontend=DEBUG 9830
    then Click the DEBUG icon “Attach debugger to sbt shell”

## Logging


### Production Logging
We wanted to create a separate Logback logger for our application code, rather than use the default Play logger.
This enables us to configure the two separately. 
We have introduced a proxy class `logging.Logger` that delegates to `play.api.Logger("customs-rosm-frontend")`.
This also gets around problems with coverage being reduced when you introduce local variable eg
 `val logger = Logger("customs-rosm-frontend")`.

### Logging in Tests
Test logging configuration can be found under `test\resources\logback.xml`.

## Code Quality

To assist with ensuring code quality we are using Scalastyle. You can test for quality pre-commit by running

    sbt precheck
    
To include Scoverage in the above check we also have the following script

    ./precheck.sh
    
nb. we hope to have Scoverage included in the `sbt` command soon though it's "sticky" nature is proving problematic

#### [Scoverage](https://github.com/scoverage/sbt-scoverage)

We're using Scoverage to check the code coverage of our test suites.

You can run this on the command line with

    sbt clean coverage test it:test coverageReport
    
Or from with SBT using the (slightly ungainly)

    ; clean ; coverage ; test ; it:test ; coverageReport
    
Adjust the following in `build.sbt` to configure Scoverage

    ...
    ScoverageKeys.coverageMinimum := 80,
    ScoverageKeys.coverageFailOnMinimum := false,
    ...


#### Scalastyle

You can find the configuration file at `project/scalastyle-config.xml`

To run using `sbt`

    sbt scalastyle
    
To run against your test sources

    sbt test:scalastyle
    
To run in Intellij (where you have more hope of finding & fixing them)

    Analyze > Run Inspection by Name > Scalastyle    
    
More configuration options can be found [here](http://www.scalastyle.org/sbt.html) 
and the Scalastyle rules [here](http://www.scalastyle.org/rules-0.8.0.html)

## ROSM/EORI Application

When running locally you can access the start page [here](http://localhost:9830/customs/register-for-cds)

NB All subsequent pages in the flow are authentication protected so you'll need a Government Gateway login to proceed