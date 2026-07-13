package com.openclassrooms.tourguide.dto;

import com.openclassrooms.tourguide.user.User;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

public class NearbyAttractionDTO {
    private final String attractionName;
    private final Double attractionLatitude;
    private final Double attractionLongitude;
    private final Double userLatitude;
    private final Double userLongitude;
    private final Double distance;
    private final int reward;

    public NearbyAttractionDTO(Attraction attraction, VisitedLocation visitedLocation, Double distance, int reward) {
        this.attractionName= attraction.attractionName;
        this.attractionLatitude = attraction.latitude;
        this.attractionLongitude = attraction.longitude;
        this.userLatitude = visitedLocation.location.latitude;
        this.userLongitude = visitedLocation.location.longitude;
        this.distance = distance;
        this.reward = reward;
    }
}
