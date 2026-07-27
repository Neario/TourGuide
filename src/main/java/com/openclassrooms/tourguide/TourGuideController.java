package com.openclassrooms.tourguide;

import java.util.List;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

/**
 * EntryPoint for TourGuide application
 * Exposes a user's location, nearby attraction, rewards and travel offers
 */
@RestController
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;

    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }

    /**
     * Return the last user's location
     * @param userName the user's name
     * @return last user's location , with position (long & lat) and timestamp
     */
    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
    	return tourGuideService.getUserLocation(getUser(userName));
    }

    /**
     * Return five attractions closest to the user's location
     * @param userName the user's name
     * @return list of DTO with attraction name, attraction position, user position , distance and reward points
     */
    @RequestMapping("/getNearbyAttractions")
    public List<NearbyAttractionDTO> getNearbyAttractions(@RequestParam String userName) {
    	VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
    	return tourGuideService.getNearbyAttractionsDto(visitedLocation, getUser(userName));
    }

    /**
     * Return rewards obtained by user
     * @param userName the user's name
     * @return list of rewards
     */
    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
    	return tourGuideService.getUserRewards(getUser(userName));
    }

    /**
     * Return travel deals for user with their rewards points
     * @param userName the user's name
     * @return list of travel deals available
     */
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }

    /**
     * Return internal user according to its name
     * @param userName the user's name
     * @return internal user or null if user doesn't exist
     */
    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}