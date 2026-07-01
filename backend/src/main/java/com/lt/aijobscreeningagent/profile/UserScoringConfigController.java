package com.lt.aijobscreeningagent.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class UserScoringConfigController {

  private final UserScoringConfigService userScoringConfigService;

  public UserScoringConfigController(UserScoringConfigService userScoringConfigService) {
    this.userScoringConfigService = userScoringConfigService;
  }

  @PostMapping("/generate-scoring-config")
  public UserScoringConfigResponse generateScoringConfig() {
    return userScoringConfigService.generateDefaultConfig();
  }

  @GetMapping("/scoring-config")
  public UserScoringConfigResponse getScoringConfig() {
    return userScoringConfigService.getDefaultConfig();
  }

  @PostMapping("/scoring-config/confirm")
  public UserScoringConfigResponse confirmScoringConfig() {
    return userScoringConfigService.confirmDefaultConfig();
  }
}
