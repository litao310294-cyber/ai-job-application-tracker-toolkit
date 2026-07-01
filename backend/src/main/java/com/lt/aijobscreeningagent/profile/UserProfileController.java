package com.lt.aijobscreeningagent.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

  private final UserProfileService userProfileService;

  public UserProfileController(UserProfileService userProfileService) {
    this.userProfileService = userProfileService;
  }

  @PostMapping("/manual")
  public UserProfileResponse saveManualProfile(@RequestBody UserProfileRequest request) {
    return userProfileService.saveManualProfile(request);
  }

  @GetMapping("/current")
  public UserProfileResponse getCurrentProfile() {
    return userProfileService.getCurrentProfile();
  }
}
