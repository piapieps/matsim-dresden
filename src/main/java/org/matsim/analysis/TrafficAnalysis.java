package org.matsim.analysis;

import org.matsim.vehicles.Vehicle;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

import java.util.HashMap;
import java.util.Map;

public class TrafficAnalysis {
	public static void main(String[] args){
		// Define paths to the required files such as the output-events-file of the Dresden scenario and the network input file
		String eventsFilePath = "output_base/output-dresden-1pct/ITERS/it.0/dresden-1pct.0.events.xml.gz";
		String networkFilePath = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/dresden/dresden-v1.0/input/dresden-v1.0-network-with-pt.xml.gz";

		System.out.println("==========================================================================");
		System.out.println("    STARTING THE ANALYSIS OF THE MODAL SPLIT IN THE DRESDEN SCENARIO      ");
		System.out.println("==========================================================================");
		System.out.println("-> Loading network infrastructure for distance calculations.");

		// Initialize the empty network object container
		Network network = NetworkUtils.createNetwork();
		MatsimNetworkReader networkReader = new MatsimNetworkReader(network);
		try{
			// Read and parse the Dresden network
			networkReader.readFile(networkFilePath);
			System.out.println("-> Network successfully loaded.");
		} catch (Exception e){
			System.out.println("Error: Unable to read the network file:" + e.getMessage());
			return;
		}

		System.out.println("-> Initializing the event managers.");
		// Initializing the core manager responsible for channeling simulation events
		EventsManager eventsManager = EventsUtils.createEventsManager();

		// Initiate the custom modal tracking handler and connect it to event manager
		ModalSplitHandler modalSplitHandler = new ModalSplitHandler(network);
		eventsManager.addHandler(modalSplitHandler);

		MatsimEventsReader reader = new MatsimEventsReader(eventsManager);
		try {
			// Process the event file link by link
			reader.readFile(eventsFilePath);
		} catch (Exception e){
			System.out.println("Error: Failed to read the events file:" + e.getMessage());
			return;
		}

		System.out.println("==========================================================================");
		System.out.println("                         ANALYSIS RESULTS                                 ");
		System.out.println("==========================================================================");
		modalSplitHandler.printResults();
		System.out.println("--------------------------------------------------------------------------");
	}

	/**
	 * Internal handler class to monitor and aggregate transportation metrics
	 */
	private static class ModalSplitHandler implements PersonDepartureEventHandler, PersonArrivalEventHandler, LinkLeaveEventHandler, PersonEntersVehicleEventHandler {
		private final Network network;

		// State tracking structures mapped to active agents ID
		private final Map<Id<Vehicle>, Id<Person>> vehicleToPersonMap = new HashMap<>();
		private final Map<Id<Person>, String> currentPersonMode = new HashMap<>();
		private final Map<Id<Person>, Double> currentPersonDistance = new HashMap<>();

		// Result aggregation structures mapped to specific transport mode
		private final  Map<String, Integer> tripCounts = new HashMap<>();
		private final Map<String, Double> totalPassengerKilometers = new HashMap<>();

		// Global aggregate scoreboards
		private int totalTripsGlobal = 0;
		private double totalKilometersGlobal = 0.0;

		public ModalSplitHandler(Network network) {
			this.network = network;
		}

		@Override
		public void handleEvent(PersonDepartureEvent event) {
			Id<Person> personId = event.getPersonId();
			String mode = event.getLegMode();

			// Fallback handling
			if (mode == null || mode.isEmpty()) {
				mode = "unknown";
			}

			// Increment metrics for active mode categories
			tripCounts.put(mode, tripCounts.getOrDefault(mode, 0) + 1);
			totalTripsGlobal++;

			// Initialize tracking maps for the active traveler
			currentPersonMode.put(personId, mode);
			currentPersonDistance.put(personId, 0.0);
		}

		@Override
		public void handleEvent(PersonEntersVehicleEvent event){
			// Links vehicle elements back to their respective agents
			vehicleToPersonMap.put(event.getVehicleId(), event.getPersonId());
		}

		@Override
		public void handleEvent(LinkLeaveEvent event){
			// Resolve driver identity using the unique vehicle identification string
			Id<Person> personId = vehicleToPersonMap.get(event.getVehicleId());

			// Verify if the driver is registered and being calculated
			if (currentPersonDistance.containsKey(personId)){
				Link link = network.getLinks().get(event.getLinkId());
				if (link != null){
					double linkLengthInMeters = link.getLength();
					double currentDistance = currentPersonDistance.get(personId);
					// Add the physical link segment to the person's total trip distance
					currentPersonDistance.put(personId, currentDistance + linkLengthInMeters);
				}
			}
		}

		@Override
		public void handleEvent(PersonArrivalEvent event){
			Id<Person> personId = event.getPersonId();

			// Collect metrics if passengers has a tracked departure context
			if (currentPersonMode.containsKey(personId) && currentPersonDistance.containsKey(personId)){
				String mode = currentPersonMode.get(personId);
				double totalMeters = currentPersonDistance.get(personId);

				// Convert metrics meter evaluation to the standard kilometer representation
				double totalKilometers = totalMeters / 1000.0;

				double existingKilometers = totalPassengerKilometers.getOrDefault(mode, 0.0);
				totalKilometersGlobal += totalKilometers;

				totalPassengerKilometers.put(mode, existingKilometers + totalKilometers);

				// Purge data entries to free up runtime heap space
				currentPersonMode.remove(personId);
				currentPersonDistance.remove(personId);
			}
		}

		/**
		 * Prints a summary table displaying trips and pkm by transport mode.
		 */
		public void printResults(){
			System.out.printf(" %-15s | %-22s | %-22s \n", "Transport Mode", "Trips Count (%)", "Passenger Kilometers (%)");
			System.out.println("--------------------------------------------------------------------------");

			if (totalTripsGlobal == 0){
				System.out.println("No matching trip record elements were found.");
				return;
			}

			// Safely format and display rows for each tracking category
			for (String mode : tripCounts.keySet()) {
				int trips = tripCounts.getOrDefault(mode, 0);
				double tripsPercentage = ((double) trips / totalTripsGlobal) * 100.0;

				double pkm = totalPassengerKilometers.getOrDefault(mode, 0.0);
				double pkmPercentage = totalKilometersGlobal > 0 ? (pkm / totalKilometersGlobal) * 100.0 :0.0;

				System.out.printf(" %-15s | %7d trips (%6.2f%%) | %10.2f pkm (%6.2f%%)\n", mode, trips, tripsPercentage, pkm, pkmPercentage);
			}

			System.out.printf(" %-15s | %7d trips %s | %10.2f pkm %s\n", "Total", totalTripsGlobal, "(100.00%)", totalKilometersGlobal, "(100.00%)");

		}
	}
}
