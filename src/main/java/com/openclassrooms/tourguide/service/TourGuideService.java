package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tripPricer.Provider;
import tripPricer.TripPricer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Central application service, user location tracking, reward calculation, travel deals
 */
@Service
public class TourGuideService {
    private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;
    private final TripPricer tripPricer = new TripPricer();
    public final Tracker tracker;
    boolean testMode = true;

    ExecutorService executor = Executors.newFixedThreadPool(100);

    public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;

        Locale.setDefault(Locale.US);

        if (testMode) {
            logger.info("TestMode enabled");
            logger.debug("Initializing users");
            initializeInternalUsers();
            logger.debug("Finished initializing users");
        }
        tracker = new Tracker(this);
        addShutDownHook();
    }

    /**
     * @param user User
     * @return rewards already earns by user
     */
    public List<UserReward> getUserRewards(User user) {
        return user.getUserRewards();
    }

    /**
     * Retuns the last location visited by user or track actual user's location if it has not already location saved
     * @param user User
     * @return the last visited position by user
     */
    public VisitedLocation getUserLocation(User user) {
        VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
                : trackUserLocation(user);
        return visitedLocation;
    }

    /**
     * Return internal user with this username
     * @param userName the user's name
     * @return the internal user or null
     */
    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    /**
     * @return all internal users saved
     */
    public List<User> getAllUsers() {
        return internalUserMap.values().stream().collect(Collectors.toList());
    }


    /**
     * Save a new user if usernane don"t exist
     * @param user User
     */
    public void addUser(User user) {
        if (!internalUserMap.containsKey(user.getUserName())) {
            internalUserMap.put(user.getUserName(), user);
        }
    }


    /**
     * Calculate travel offers available for user with accumulated rewards point
     * @param user User
     * @return list of travel offers
     */
    public List<Provider> getTripDeals(User user) {
        int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
        List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
        user.setTripDeals(providers);
        return providers;
    }

    /**
     * Track actual user's location and add to their history.
     * Calculate rewards point for this new location
     * @param user User
     * @return Visited location who has been saved
     */
    public VisitedLocation trackUserLocation(User user) {
        VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
        user.addToVisitedLocations(visitedLocation);
        rewardsService.calculateRewards(user);
        return visitedLocation;
    }

    /**
     * Locates actual location for all users
     * Calculate in parallel with {@link CompletableFuture} and pool {@link ExecutorService}
     * @param users list of users for tracking
     * @return list of visitedLocation
     */
    public List<VisitedLocation> trackUsersLocation(List<User> users) {

        List<CompletableFuture<VisitedLocation>> completableFutures = users.stream()
                .map(user -> CompletableFuture.supplyAsync(() -> trackUserLocation(user), executor))
                .toList();

        return completableFutures.stream()
                .map(CompletableFuture::join)
                .toList();

    }

    /**
     * Return the 5 tourist attractions closest to the visitedLocation, sorted by distance, without proximity
     * @param visitedLocation VisitedLocation
     * @return the 5 closest tourist attractions
     */
    public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
        return gpsUtil.getAttractions().stream().sorted(Comparator.comparingDouble(attraction ->
                        rewardsService.getDistance(attraction, visitedLocation.location)))
                .limit(5)
                .toList();
    }

    /**
     * For each closet tourist attractions a DTO with the attraction position (long, lat),
     * the user position (long, lat), the distance in miles and associated rewards point
     * @param visitedLocation VisitedLocation
     * @param user User
     * @return list of nearby attractions with added information in DTO
     */
    public List<NearbyAttractionDTO> getNearbyAttractionsDto(VisitedLocation visitedLocation, User user) {
        return getNearByAttractions(visitedLocation).stream().map(attraction ->
                new NearbyAttractionDTO(attraction, visitedLocation, rewardsService.getDistance(attraction,
                        visitedLocation.location), rewardsService.getRewardPoints(attraction, user))
        ).toList();
    }

    private void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                tracker.stopTracking();
            }
        });
    }

    /**********************************************************************************
     *
     * Methods Below: For Internal Testing
     *
     **********************************************************************************/
    private static final String tripPricerApiKey = "test-server-api-key";
    // Database connection will be used for external users, but for testing purposes
    // internal users are provided and stored in memory
    private final Map<String, User> internalUserMap = new HashMap<>();

    private void initializeInternalUsers() {
        IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
            String userName = "internalUser" + i;
            String phone = "000";
            String email = userName + "@tourGuide.com";
            User user = new User(UUID.randomUUID(), userName, phone, email);
            generateUserLocationHistory(user);

            internalUserMap.put(userName, user);
        });
        logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
    }

    private void generateUserLocationHistory(User user) {
        IntStream.range(0, 3).forEach(i -> {
            user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
                    new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
        });
    }

    private double generateRandomLongitude() {
        double leftLimit = -180;
        double rightLimit = 180;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private double generateRandomLatitude() {
        double leftLimit = -85.05112878;
        double rightLimit = 85.05112878;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private Date getRandomTime() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

}
