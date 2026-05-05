package org.matsim.run.scenarios;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimApplication;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Set;

public class DresdenRemoveLink extends DresdenScenario{
	public static void main(String[] args){
		MATSimApplication.execute(DresdenRemoveLink.class, args);
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


		scenario.getNetwork().getLinks().get(Id.createLinkId("318199257")).setAllowedModes(Set.of());

	//scenario.getNetwork().removeLink(Id.createLinkId("318199257"));

		ScenarioUtils.cleanScenario(scenario);
	}
}
