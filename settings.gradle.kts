rootProject.name = "back"

include("libs:common")
include("libs:common-kafka")
include("libs:event-contracts")
include("services:member-service")
include("services:order-service")
include("services:delivery-service")
include("services:payment-service")