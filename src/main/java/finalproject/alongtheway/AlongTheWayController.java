package finalproject.alongtheway;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.ModelAndView;

import finalproject.alongtheway.dao.Route;
import finalproject.alongtheway.dao.RoutesDao;
import finalproject.alongtheway.dao.Stop;
import finalproject.alongtheway.waypointsbeans.Legs;
import finalproject.alongtheway.waypointsbeans.Steps;
import finalproject.alongtheway.yelpbeans.Businesses;
import finalproject.alongtheway.yelpbeans.Coordinates;

@Controller
public class AlongTheWayController {

	@Autowired
	RoutesDao dao;

	@Autowired
	private GoogleApiService googleApiService;

	@Autowired
	private BusinessSearchApiService businessSearchService;

	@RequestMapping("/")
	public ModelAndView index(HttpSession session) {
		return new ModelAndView("index");
	}

	@RequestMapping("/endsession")
	public ModelAndView endsession(HttpSession session) {
		session.invalidate();
		return new ModelAndView("redirect:/");
	}
	
	@RequestMapping("/submitform")
	public ModelAndView formsubmit(
			@RequestParam(name = "location1") String location1,
			@RequestParam(name = "location2") String location2, 
			@RequestParam(name = "category") String category,
			@RequestParam(name = "minrating") Double minrating,
			HttpSession session) {
		
		// when first clicking submit button from index page, add the variables to the session
		session.setAttribute("location1", location1);
		session.setAttribute("location2", location2);
		session.setAttribute("category", category);
		session.setAttribute("minrating", minrating);
		ModelAndView mav = new ModelAndView("redirect:/results");
		return mav;
	}

	@RequestMapping("/contacts")
	public ModelAndView contacts() {
		return new ModelAndView("contacts");
	}

	@RequestMapping("/add")
	public ModelAndView add(
			@RequestParam(name = "yelpid") String yelpId,
			@SessionAttribute(name = "location1") String location1,
			@SessionAttribute(name = "location2") String location2,
			@SessionAttribute(name = "category") String category,
			HttpSession session) {

		@SuppressWarnings("unchecked")
		List<Stop> stops = (List<Stop>) session.getAttribute("stops");
		if (stops == null) {
			stops = new ArrayList<Stop>();
			session.setAttribute("stops", stops);
		}

		// get the business as a Businesses class object at the stop by yelp id
		Businesses busi = businessSearchService.getResultById(yelpId);

		// create a new stop to be added to the list of stops
		// using the business returned by the yelpid, set the parameters
		Stop stop = new Stop();
		stop.setYelpId(yelpId);
		stop.setName(busi.getName());
		stop.setCity(busi.getLocation().getCity());
		stop.setState(busi.getLocation().getState());

		// add this created stop to the session List<Stop> stops
		stops.add(stop);

		// redirect to the results page, no need to add objects to model since the same info is already in the session
		ModelAndView mav = new ModelAndView("redirect:/results");

		return mav;
	}

	@RequestMapping("/saveroute")
	public ModelAndView saveroute(
			@SessionAttribute(value = "location1", required = true) String location1,
			@SessionAttribute(value = "location2", required = true) String location2,
			@SessionAttribute(value = "stops", required = false) List<Stop> stops,
			HttpSession session) {
		Route route = new Route();
		route.setLocation1(location1);
		route.setLocation2(location2);
		route.setStops(stops);
		dao.create(route);
		return new ModelAndView("redirect:/results");
	}
	
	
	@RequestMapping("/dt")
	public ModelAndView dist(
			@SessionAttribute(value = "location1", required = true) String location1,
			@SessionAttribute(value = "location2", required = true) String location2,
			@SessionAttribute(value = "stops", required = false) List<Stop> stops) {

		ModelAndView mav = new ModelAndView("test");

		Legs legs = googleApiService.getAmendedDirections(location1, location2, stops);
		String distNew;
		String timeNew;

		return mav;
	}

	@RequestMapping("/matrix")
	public ModelAndView showRoutes(HttpSession session) {
		// return list of all items in DB and pass to model
		List<Route> theRoutes = dao.findAll();
		return new ModelAndView("matrix", "amend", theRoutes);
	}

	@RequestMapping("/delete")
	public ModelAndView deleteRouteForm(@RequestParam("id") Long id) {
		dao.delete(id);
		ModelAndView mav = new ModelAndView("redirect:/matrix");
		return mav;
	}

//	 when populating the results page, we want to return the set of results
//	 generated from each waypoint along the way as a single list
	@RequestMapping("/results")
	public ModelAndView results(
			@SessionAttribute(name = "location1") String location1,
			@SessionAttribute(name = "location2") String location2,
			@SessionAttribute(name = "category") String category,
			@SessionAttribute(name = "minrating") Double minrating,
			@SessionAttribute(name = "stops") List<Stop> stops,
			HttpSession session) {
		
		// define the steps along the way from the google directions api
		List<Steps> steps = googleApiService.getWaypoints(location1, location2);
		
		// initialize the waypoints to be a list of Coordinates (paired lat and long)
		List<Coordinates> waypoints = new ArrayList<Coordinates>();
		// set save the waypoints list to the session
		session.setAttribute("waypoints", waypoints);

		int i = 0;
		for (Steps stepwp : steps) {
			// set the first coordinates in the list to be the starting location, as steps doesn't include location 0
			if (i == 0) {
				Coordinates coord = new Coordinates();
				coord.setLatitude(stepwp.getStartLocation().getStartLat());
				coord.setLongitude(stepwp.getStartLocation().getStartLong());
				waypoints.add(coord);
			}
			// store each lat and long from the list of steps in a list of Coordinates called waypoints
			Coordinates coord = new Coordinates();
			coord.setLatitude(stepwp.getEndLocation().getEndLat());
			coord.setLongitude(stepwp.getEndLocation().getEndLong());
			waypoints.add(coord);
			i++;
		}

		// fullResults will be a list of all results from all waypoints
		List<Businesses> fullResults = new ArrayList<Businesses>();

		for (Coordinates coordinates : waypoints) {
			// results will take in the yelp response from each waypoint
			List<Businesses> results = businessSearchService.getAllResultsByCoordByCategory(coordinates.getLatitude(),
					coordinates.getLongitude(), category);

			List<String> names = new ArrayList<String>();
			
			for (Businesses busi : results) {
				if (!names.contains(busi.getId())) {
					names.add(busi.getId());
					// return fullResults from all waypoints for items rated 4.0 or higher
					if (busi.getRating() >= minrating) {
						fullResults.add(busi);
					}
				}
			}
		}

		session.setAttribute("location1", location1);
		session.setAttribute("location2", location2);
		session.setAttribute("category", category);

		Legs leg = googleApiService.getBasicDirections(location1, location2);
		String dist = leg.getDistance().getText();
		String time = leg.getDuration().getText();

		ModelAndView mav = new ModelAndView("results", "results", fullResults);

		String[] parseLoc1 = location1.split(",");
		String[] parseLoc2 = location2.split(",");
		mav.addObject("loc1", parseLoc1[0] + "+" + parseLoc1[1]);
		mav.addObject("loc2", parseLoc2[0] + "+" + parseLoc2[1]);

		// basic dist/time
		mav.addObject("distance", dist);
		mav.addObject("duration", time);
		return mav;
	}

	// when the user clicks from results page to details
	@RequestMapping("/details/{id}")
	public ModelAndView details(@PathVariable(value = "id") String id) {
		Businesses result = businessSearchService.getResultById(id);
		ModelAndView mav = new ModelAndView("details", "result", result);
		return mav;
	}

	private String splitter(String str1) {

		String[] splitStr = str1.split("\\s+");

		if (!splitStr[1].isEmpty()) {
			str1 = splitStr[0] + "+" + splitStr[1] + splitStr[2];
			System.out.println("not empty" + str1);
		}

		return str1;
	}
}
