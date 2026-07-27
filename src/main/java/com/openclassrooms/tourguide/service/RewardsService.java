package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import static java.util.concurrent.CompletableFuture.runAsync;

/**
 * Calculate rewards point a user earns for attractions visited
 * based between locations user and location attractions
 */
@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

    ExecutorService executorService = Executors.newFixedThreadPool(100);

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

    /**
     * Iterates list of locations visited by the user , and the list of attraction
     * to determine if the user war near an attraction, if they haven't received rewards point,
     * the rewards points are added
     * @param user User with list of visitedLocations
     */
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<Attraction> attractions = gpsUtil.getAttractions();

        Set<String> rewardAttractions = user.getUserRewards().stream()
                .map(reward -> reward.attraction.attractionName)
                .collect(Collectors.toSet());

        for(VisitedLocation visitedLocation : userLocations) {
            rewardNearAttraction(user, attractions, visitedLocation,  rewardAttractions);
		}
	}

    /**
     * For a visitedLocation adds rewards point for each nearby attraction if haven't already rewards point.
     * @param user User for reward
     * @param attractions list of all attractions
     * @param visitedLocation the visited location to compare at attraction location
     * @param rewardAttractions set of attraction already rewarded
     */
    private void rewardNearAttraction(User user, List<Attraction> attractions, VisitedLocation visitedLocation,
                                      Set<String> rewardAttractions) {
        for (Attraction attraction : attractions) {
            if (!rewardAttractions.contains(attraction.attractionName) && nearAttraction(visitedLocation, attraction)) {
                user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
            }
        }
    }

    /**
     * Calculate rewards for all users.
     * Calculate in parallel with {@link CompletableFuture} and pool {@link ExecutorService}
     * @param users list of users for calculate rewards
     */
    public void calculateRewards(List<User> users) {
        List<CompletableFuture<Void>> completableFutures = users
                .stream()
                .parallel()
                .map(user -> runAsync(() -> calculateRewards(user), executorService))
                .toList();

        completableFutures.forEach(CompletableFuture::join);

    }

    /**
     * Check if an attraction and a location is less than 200 miles
     * @param attraction attraction to calculate proximity
     * @param location position for calculate proximity
     * @return boolean , {@code true} ud the location is less than 200 miles
     */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}

    /**
     * Check if visited location is less than 10 miles {@code proximityBuffer}
     * @param visitedLocation visitedLocation for calculate proximity (10 miles)
     * @param attraction attraction for calculate proximity (10 miles)
     * @return boolean , {@code true} ud the location is less than 10 miles
     */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}


    /**
     * @param attraction attraction visited
     * @param user User
     * @return rewards point awarded by {@code rewardsCentral} for this attraction
     */
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

    /**
     * calculate distance between two locations
     * @param loc1 first location
     * @param loc2 second location
     * @return distance between two locations in miles
     */
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
