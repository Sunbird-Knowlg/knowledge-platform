package utils

object ApiId {

	final val APPLICATION_HEALTH = "api.content-service.health"

	//Content APIs
	final val CREATE_CONTENT = "ekstep.learning.content.create"
	final val READ_CONTENT = "ekstep.content.find"
	final val UPDATE_CONTENT = "ekstep.learning.content.update"

	// Collection APIs
	val ADD_HIERARCHY = "api.content.hierarchy.add"
	val REMOVE_HIERARCHY = "api.content.hierarchy.remove"

	//LicenseAPIS
	final val CREATE_LICENSE = "api.license.create"
	final val READ_LICENSE = "api.license.read"
	final val UPDATE_LICENSE = "api.license.update"
	final val RETIRE_LICENSE = "api.license.retire"
}
