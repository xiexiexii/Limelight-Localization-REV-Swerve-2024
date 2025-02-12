// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.Swerve;

import java.util.List;

import com.kauailabs.navx.frc.AHRS;
import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.commands.FollowPathCommand;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import com.pathplanner.lib.path.GoalEndState;
import com.pathplanner.lib.path.PathConstraints;
import com.pathplanner.lib.path.PathPlannerPath;
import com.pathplanner.lib.path.PathPlannerTrajectory;
import com.pathplanner.lib.util.HolonomicPathFollowerConfig;
import com.pathplanner.lib.util.PIDConstants;
import com.pathplanner.lib.util.ReplanningConfig;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.filter.SlewRateLimiter;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.util.WPIUtilJNI;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;
import frc.robot.Constants.DriveConstants;
import frc.robot.Constants.VisionConstants;
import frc.robot.subsystems.Limelight.LimelightHelpers;
import frc.robot.subsystems.Limelight.Localization;
import frc.utils.SwerveUtils;

// Drive Subsystem class wahuuu
public class DriveSubsystem extends SubsystemBase {

  // Creates MAX Swerve Modules
  private final MAXSwerveModule m_frontLeft = new MAXSwerveModule(
    DriveConstants.kFrontLeftDrivingCanId,
    DriveConstants.kFrontLeftTurningCanId,
    DriveConstants.kFrontLeftChassisAngularOffset);

  private final MAXSwerveModule m_frontRight = new MAXSwerveModule(
    DriveConstants.kFrontRightDrivingCanId,
    DriveConstants.kFrontRightTurningCanId,
    DriveConstants.kFrontRightChassisAngularOffset);

  private final MAXSwerveModule m_backLeft = new MAXSwerveModule(
    DriveConstants.kRearLeftDrivingCanId,
    DriveConstants.kRearLeftTurningCanId,
    DriveConstants.kBackLeftChassisAngularOffset);
  
  private final MAXSwerveModule m_backRight = new MAXSwerveModule(
    DriveConstants.kRearRightDrivingCanId,
    DriveConstants.kRearRightTurningCanId,
    DriveConstants.kBackRightChassisAngularOffset);

    private MAXSwerveModule[] modules = new MAXSwerveModule[]{
      m_frontLeft,
      m_frontRight,
      m_backLeft,
      m_backRight
    };

  // Creates the Gyro for Swerve Magic
  private final AHRS m_gyro = new AHRS();

  private final Field2d m_field = new Field2d();
  // Red Alliance sees forward as 180 degrees, Blue Alliance sees as 0
  public static int AllianceYaw;

  // Slew Rate Variables & Objects - Change of Voltage per microsecond
  private double m_currentRotation = 0.0;
  private double m_currentTranslationDir = 0.0;
  private double m_currentTranslationMag = 0.0;

  private SlewRateLimiter m_magRateLimiter = new SlewRateLimiter(DriveConstants.kMagnitudeSlewRate);
  private SlewRateLimiter m_rotRateLimiter = new SlewRateLimiter(DriveConstants.kRotationalSlewRate);
  private double m_prevTime = WPIUtilJNI.now() * 1e-6;

  private Rotation2d rawGyroRotation = new Rotation2d();
  public static final PIDConstants translationalPID = new PIDConstants(0.824, 0.95, 0.15);
  public static final PIDConstants rotationalPID = new PIDConstants(0.23, 0, 0.01);

  public static final HolonomicPathFollowerConfig config = new HolonomicPathFollowerConfig(translationalPID, rotationalPID,
    5.7, DriveConstants.kWheelBase/Math.sqrt(2), new ReplanningConfig());

  // Pose Estimation - Tracks robot pose
  private SwerveDrivePoseEstimator m_poseEstimator = new SwerveDrivePoseEstimator(
    DriveConstants.kDriveKinematics, 
    Rotation2d.fromDegrees(m_gyro.getYaw()), 
    new SwerveModulePosition[] {
      m_frontLeft.getPosition(),
      m_frontRight.getPosition(),
      m_backLeft.getPosition(),
      m_backRight.getPosition()
    },
    new Pose2d());
  
  // Creates a new DriveSubsystem
  public DriveSubsystem() {

    AutoBuilder.configureHolonomic(
      this::getPose,
      this::setPose, 
      () -> DriveConstants.kDriveKinematics.toChassisSpeeds(getModuleStates()), 
      this::runVelocity,
      config, 
      () -> {
        var alliance = DriverStation.getAlliance();
        if (alliance.isPresent()) {
          return alliance.get() == DriverStation.Alliance.Red;
        }
        return false;
      },
        this);
  }

  @Override
  // This method will be called once per scheduler run
  // Periodically update the odometry
  public void periodic() {
    updateVisionMeasurements();
    
    m_poseEstimator.update(
      Rotation2d.fromDegrees(-m_gyro.getYaw()), 
      new SwerveModulePosition[] {
        m_frontLeft.getPosition(),
        m_frontRight.getPosition(),
        m_backLeft.getPosition(),
        m_backRight.getPosition()
      });
    /* 
      boolean doRejectUpdate = false;

      LimelightHelpers.SetRobotOrientation("", -m_gyro.getYaw(), 0, 0, 0, 0, 0);
      LimelightHelpers.PoseEstimate mt2 = LimelightHelpers.getBotPoseEstimate_wpiBlue_MegaTag2("");

      if(mt2 == null) {
        doRejectUpdate = true;
      }
      if(Math.abs(m_gyro.getRate()) > 720) // if our angular velocity is greater than 720 degrees per second, ignore vision updates
      {
        doRejectUpdate = true;
      }
      if(mt2.tagCount == 0)
      {
        doRejectUpdate = true;
      }
      if(!doRejectUpdate)
      {
        m_poseEstimator.setVisionMeasurementStdDevs(VecBuilder.fill(.7,.7,9999999));
        m_poseEstimator.addVisionMeasurement(
            mt2.pose,
            mt2.timestampSeconds);
      }
      */

      // puts field onto smart dashboard
      SmartDashboard.putData("Field", m_field);

      // Puts Yaw + Angle on Smart Dashboard, as well as Limelight MT2 Field Localization
      SmartDashboard.putNumber("NavX Yaw", -m_gyro.getYaw());
      SmartDashboard.putNumber("NavX Angle", m_gyro.getAngle());
      // SmartDashboard.putNumber("Limelight Angle", m_poseEstimator.getEstimatedPosition().getRotation().getDegrees());
      SmartDashboard.putNumber("TX", LimelightHelpers.getTX("limelight-three"));
      SmartDashboard.putNumber("TY", LimelightHelpers.getTY("limelight-three"));
  }

  // Updates Odometry with the Limelight Readings using MT2 - Old, use updateVisionMeasurements()
  public void updateVisionOdometry() {

    // Used to stop updating upon condiditions
    boolean doRejectUpdate = false;

    // Setting Yaw to Compensate for Red Alliance Limelight Localization
    var alliance = DriverStation.getAlliance();
    if (alliance.isPresent()){
      if (alliance.get() == DriverStation.Alliance.Red) {
        AllianceYaw = 180;
      }
      else if (alliance.get() == DriverStation.Alliance.Blue){
        AllianceYaw = 0;
      }
    }

    // Gets the robot's yaw for LL, then gets a field pose estimate using MT2
    // IMPORTANT: LOOK AT THE NOTE ABOVE FOR THE ALLIANCE YAW VARIABLE!!!
    LimelightHelpers.SetRobotOrientation("limelight-three", -m_gyro.getYaw() + AllianceYaw, 0, 0, 0, 0, 0);
    LimelightHelpers.PoseEstimate mt2Estimate = LimelightHelpers.getBotPoseEstimate_wpiBlue_MegaTag2("limelight-three");
    
    // If angular velocity is greater than 720 deg/s, ignore vision updates
    if (Math.abs(m_gyro.getRate()) > 720) {
      doRejectUpdate = true;
    }

    // If there are no tags in sight, ignore vision updates
    if (mt2Estimate == null || mt2Estimate.tagCount == 0) {
      doRejectUpdate = true;
    }

    // If all conditions are met, update vision
    if (!doRejectUpdate && mt2Estimate != null) {
      m_poseEstimator.addVisionMeasurement(mt2Estimate.pose, mt2Estimate.timestampSeconds, VecBuilder.fill(.7,.7,9999999));
    }
  }

  // Updates Odometry with the Limelight Readings using MT2 - Replacement for updateVisionOdometry()
  public void updateVisionMeasurements() {

    // Setting Yaw to Compensate for Red Alliance Limelight Localization
    var alliance = DriverStation.getAlliance();
    if (alliance.isPresent()){
      if (alliance.get() == DriverStation.Alliance.Red) {
        AllianceYaw = 180;
      }
      else if (alliance.get() == DriverStation.Alliance.Blue){
        AllianceYaw = 0;
      }
    }

    // For each limelight...
    for (Localization.LimelightPoseEstimateWrapper estimateWrapper : Localization.getPoseEstimates(getHeading())) {

      // If there is a tag in view and the pose estimate is valid...
      if (estimateWrapper.tiv && poseEstimateIsValid(estimateWrapper.poseEstimate)) {

        // Add the vision measurement to the swerve drive
        m_poseEstimator.addVisionMeasurement(estimateWrapper.poseEstimate.pose,
          estimateWrapper.poseEstimate.timestampSeconds,
          estimateWrapper.getStdvs(estimateWrapper.poseEstimate.avgTagDist));

        // Update position on Field2d
        // m_field.setRobotPose(estimateWrapper.poseEstimate.pose);
        // SmartDashboard.putNumber("local x",estimateWrapper.poseEstimate.pose.getX());
        // SmartDashboard.putNumber("local y",estimateWrapper.poseEstimate.pose.getY());
      }
    }
    
    m_field.setRobotPose(m_poseEstimator.getEstimatedPosition());
    SmartDashboard.putNumber("local x", m_poseEstimator.getEstimatedPosition().getX());
    SmartDashboard.putNumber("local y", m_poseEstimator.getEstimatedPosition().getY());
  }
  // uses localization to drive to specific pose
  public void goToDesiredPose(Pose2d desiredPose){
    // Create a list of waypoints from poses. Each pose represents one waypoint.
    // The rotation component of the pose should be the direction of travel. Do not use holonomic rotation.
    List<Translation2d> waypoints = PathPlannerPath.bezierFromPoses(
        getPose(),
        desiredPose
    );

    PathConstraints constraints = new PathConstraints(3.0, 3.0, 2 * Math.PI, 4 * Math.PI); // The constraints for this path.
    // PathConstraints constraints = PathConstraints.unlimitedConstraints(12.0); // You can also use unlimited constraints, only limited by motor torque and nominal battery voltage

    // Create the path using the waypoints created above
    PathPlannerPath path = new PathPlannerPath(
          waypoints,
          constraints,
          new GoalEndState(0,desiredPose.getRotation()) // The ideal starting state, this is only relevant for pre-planned paths, so can be null for on-the-fly paths.
    );
    AutoBuilder.followPath(path).schedule();
  }
  // Check if pose estimate is valid
  private boolean poseEstimateIsValid(LimelightHelpers.PoseEstimate estimate) {
    return estimate != null && Math.abs(getTurnRate()) < VisionConstants.rejectionRotationRate
      && estimate.avgTagDist < VisionConstants.rejectionDistance;
  }

  // Returns currently estimated pose of robot
  public Pose2d getPose() {
    return m_poseEstimator.getEstimatedPosition();
  }

  public void setPose(Pose2d pose) {
    m_poseEstimator.resetPosition(rawGyroRotation, getModulePositions(), pose);
  }

  // Resets odometry to specified pose
  public void resetOdometry(Pose2d pose) {
    m_poseEstimator.resetPosition(
      Rotation2d.fromDegrees(-m_gyro.getYaw()), 
      new SwerveModulePosition[] {
        m_frontLeft.getPosition(),
        m_frontRight.getPosition(),
        m_backLeft.getPosition(),
        m_backRight.getPosition()
      }, 
      pose);
  }

   // Method to drive the robot using joystick info
   // xSpeed         Speed of the robot in the x direction (forward).
   // ySpeed         Speed of the robot in the y direction (sideways).
   // rot            Angular rate of the robot.
   // fieldRelative  Whether the provided x and y speeds are relative to the field.
   // rateLimit      Whether to enable rate limiting for smoother control.
  public void drive(double xSpeed, double ySpeed, double rot, boolean fieldRelative, boolean rateLimit) {

    // x and y commanded speed variables
    double xSpeedCommanded;
    double ySpeedCommanded;

    // Checks for rate limiting
    if (rateLimit) {

      // Converts Cartesian XY to Polar for Rate Limiting
      // Very interesting calculus topic if you're interested!
      double inputTranslationDir = Math.atan2(ySpeed, xSpeed);
      double inputTranslationMag = Math.sqrt(Math.pow(xSpeed, 2) + Math.pow(ySpeed, 2));

      // Calculate direction slew rate based on lateral accel estimate
      double directionSlewRate;

      // If the translation magnitude isn't zero, calculate the slew rate
      if (m_currentTranslationMag != 0.0) {
        directionSlewRate = Math.abs(DriveConstants.kDirectionSlewRate / m_currentTranslationMag);
      }

      // Otherwise, set the rate to a high number (almost instantaneous)
      else {
        directionSlewRate = 500.0;
      }

      // Ensures a motor doesn't have to turn more than 90 degrees to
      // get to any position desired by the driver
      double currentTime = WPIUtilJNI.now() * 1e-6;
      double elapsedTime = currentTime - m_prevTime;
      double angleDif = SwerveUtils.AngleDifference(inputTranslationDir, m_currentTranslationDir);

      // Take the shortest path to desired pose
      if (angleDif < 0.45 * Math.PI) {
        m_currentTranslationDir = SwerveUtils.StepTowardsCircular(m_currentTranslationDir, inputTranslationDir, directionSlewRate * elapsedTime);
        m_currentTranslationMag = m_magRateLimiter.calculate(inputTranslationMag);
      }

      else if (angleDif > 0.85 * Math.PI) {

        if (m_currentTranslationMag > 1e-4) {
          // Keeps currentTranslationMag unchanged
          m_currentTranslationMag = m_magRateLimiter.calculate(0.0);
        }

        // Wraps angle to be less than 2pi
        else {
          m_currentTranslationDir = SwerveUtils.WrapAngle(m_currentTranslationDir + Math.PI);
          m_currentTranslationMag = m_magRateLimiter.calculate(inputTranslationMag);
        }
      }

      else {
        m_currentTranslationDir = SwerveUtils.StepTowardsCircular(m_currentTranslationDir, inputTranslationDir, directionSlewRate * elapsedTime);
        m_currentTranslationMag = m_magRateLimiter.calculate(0.0);
      }

      // Makes the current time the new previous time
      m_prevTime = currentTime;
      
      // Adjusts the x and y speedCommanded based on the current translation direction calculated
      xSpeedCommanded = m_currentTranslationMag * Math.cos(m_currentTranslationDir);
      ySpeedCommanded = m_currentTranslationMag * Math.sin(m_currentTranslationDir);
      m_currentRotation = m_rotRateLimiter.calculate(rot);
    }
  
    // If no rate limit, everything is normal
    else {
      xSpeedCommanded = xSpeed;
      ySpeedCommanded = ySpeed;
      m_currentRotation = rot;
    }

    // Convert commanded speed to correct units for drivetrain
    double xSpeedDelivered = xSpeedCommanded * DriveConstants.kMaxSpeedMetersPerSecond;
    double ySpeedDelivered = ySpeedCommanded * DriveConstants.kMaxSpeedMetersPerSecond;
    double rotDelivered = m_currentRotation * DriveConstants.kMaxAngularSpeed;

    // Converts field relative speeds to chassis speeds
    var SwerveModuleStates = DriveConstants.kDriveKinematics.toSwerveModuleStates(
      fieldRelative
        ? ChassisSpeeds.fromFieldRelativeSpeeds(-xSpeedDelivered, -ySpeedDelivered, rotDelivered, Rotation2d.fromDegrees(-m_gyro.getYaw()))
        : new ChassisSpeeds(-xSpeedDelivered, -ySpeedDelivered, rotDelivered));
    SwerveDriveKinematics.desaturateWheelSpeeds(SwerveModuleStates, DriveConstants.kMaxSpeedMetersPerSecond);

    // The toSwerveModuleStates converts the desired chassis speed to an array of swerve module
    // states (four). Sets the new state of each module based on the indexes of the array
    m_frontLeft.setDesiredState(SwerveModuleStates[0]);
    m_frontRight.setDesiredState(SwerveModuleStates[1]);
    m_backLeft.setDesiredState(SwerveModuleStates[2]);
    m_backRight.setDesiredState(SwerveModuleStates[3]);
  }

  // Sets wheels to X Formation to keep robot from moving
  public void setX() {
    m_frontLeft.setDesiredState(new SwerveModuleState(0, Rotation2d.fromDegrees(45)));
    m_frontRight.setDesiredState(new SwerveModuleState(0, Rotation2d.fromDegrees(-45)));
    m_backLeft.setDesiredState(new SwerveModuleState(0, Rotation2d.fromDegrees(-45)));
    m_backRight.setDesiredState(new SwerveModuleState(0, Rotation2d.fromDegrees(45)));
  }

  // Sets the Swerve Module States
  public void setModuleStates(SwerveModuleState[] desiredStates) {
    SwerveDriveKinematics.desaturateWheelSpeeds(desiredStates, DriveConstants.kMaxSpeedMetersPerSecond);
    m_frontLeft.setDesiredState(desiredStates[0]);
    m_frontRight.setDesiredState(desiredStates[1]);
    m_backLeft.setDesiredState(desiredStates[2]);
    m_backRight.setDesiredState(desiredStates[3]);
  }

  public SwerveModuleState[] getModuleStates(){
    SwerveModuleState[] currentStates = new SwerveModuleState[modules.length];
    for (int i = 0; i < modules.length; i++){
      currentStates[i] = modules[i].getState();
    }
    return currentStates;
      
  }

  public SwerveModulePosition[] getModulePositions(){
    SwerveModulePosition[] positions = new SwerveModulePosition[4];
    for (int i = 0; i <4; i++){
      positions[i] = modules[i].getPosition();
    }
    return positions;
  }

  public void runVelocity(ChassisSpeeds speeds) {
    //calculate module setpoints
    ChassisSpeeds discreteSpeeds = ChassisSpeeds.discretize(speeds, 0.02);
    SwerveModuleState[] setpointStates = DriveConstants.kDriveKinematics.toSwerveModuleStates(discreteSpeeds);
    SwerveDriveKinematics.desaturateWheelSpeeds(setpointStates, DriveConstants.kMaxSpeedMetersPerSecond);

    //send setpoints to modules
    for (int i = 0; i < 4; i++) {
      //the module returns the optimizes state, useful for logging
      modules[i].setDesiredState(setpointStates[i]);
    }
  }

  // Resets all drive encoders to read position zero
  public void resetEncoders() {
    m_frontLeft.resetEncoders();
    m_frontRight.resetEncoders();
    m_backLeft.resetEncoders();
    m_backRight.resetEncoders();
  }

  // Zeros the robot heading
  public void zeroHeading() {
    m_gyro.reset();
  }

  // DEPRECIATED: Gyro Calibration
  public void calibrateGyro() {
  }

  // Return Robot Headings, from -180 to 180 degrees
  public double getHeading() {
    return Rotation2d.fromDegrees(m_gyro.getYaw()).getDegrees();
  }

  // Returns Robot Turn Rate, in degrees per second
  public double getTurnRate() {
    return m_gyro.getRate() * (DriveConstants.kGyroReversed ? -1.0 : 1.0);
  }

  // Gets an Auto from PathPlanner
  public Command getAuto(String autoName) {
      return AutoBuilder.buildAuto(autoName);
    }
}
