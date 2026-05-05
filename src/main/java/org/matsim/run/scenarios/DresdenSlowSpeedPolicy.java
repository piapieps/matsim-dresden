package org.matsim.run.scenarios;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimApplication;
import org.matsim.core.config.Config;

public class DresdenSlowSpeedPolicy extends DresdenScenario{
	public static void main(String[] args){
		MATSimApplication.execute(DresdenSlowSpeedPolicy.class, args);
	}

	protected Config prepareConfig(Config config) {
		super.prepareConfig(config);

		// no simwrapper output is created, this saves us runtime.
		simwrapper = false;

		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory("output-remove-link");

		System.out.println("Here I am");

		return config;
	}
	protected void prepareScenario(Scenario scenario){
		super.prepareScenario(scenario);

		scenario.getNetwork().getLinks().get(Id.createLinkId("318199257")).setFreespeed(1.0);
		scenario.getNetwork().getLinks().get(Id.createLinkId("31059226")).setFreespeed(1.0);
		scenario.getNetwork().getLinks().get(Id.createLinkId("318199257")).setCapacity(500);
		scenario.getNetwork().getLinks().get(Id.createLinkId("31059226")).setCapacity(500);

		scenario.getNetwork().getLinks().get(Id.createLinkId("318199257")).setAllowedModes(null);

	}
}
