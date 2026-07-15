package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
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

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

    ExecutorService executorService = Executors.newFixedThreadPool(60);

    /**
     *
     * @param gpsUtil donne un user location aléatoire selon UUID user donné , et contient une liste d attraction
     * @param rewardCentral selon l'attraction UUID donné , donne un int aléatoire comme point de fidelité
     */
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
     * userLocations = récupere tout les lieu visités par un user
     * attractions = récupére liste de toute les attraction et de leur location
     * @param user
     */
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<Attraction> attractions = gpsUtil.getAttractions();

        Set<String> rewardAttractions = user.getUserRewards().stream()
                .map(reward -> reward.attraction.attractionName)
                .collect(Collectors.toSet());

        /**
         * parcours les attractions et la position de l'user , si l'user a ete pres d'une atraction et qu'il n a pas de point
         * alors on lui rajoute un nouveau UserReward dans sa liste
         * probleme tracker qui regarde le meme objet , user pour verifier sa location , modification pendant une iteration ConcurrentModificationException
         */
        for(VisitedLocation visitedLocation : userLocations) {
			for(Attraction attraction : attractions) {
				if(!rewardAttractions.contains(attraction.attractionName) && nearAttraction(visitedLocation, attraction)) {
                    user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
				}
			}
		}
	}

    public void calculateRewards(List<User> users) {
        List<CompletableFuture<Void>> completableFutures = users.stream()
                .map(user -> runAsync(() -> calculateRewards(user), executorService))
                .toList();

        completableFutures.forEach(CompletableFuture::join);

    }
	
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
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
