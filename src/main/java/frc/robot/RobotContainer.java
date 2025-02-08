// Imports stuff (again!)
package frc.robot;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.Constants.ControllerConstants;
import frc.robot.Constants.LocationConstants;
import frc.robot.subsystems.Swerve.DriveSubsystem;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;

// This class is where the bulk of the robot should be declared.  Since Command-based is a
// "declarative" paradigm, very little robot logic should actually be handled in the Robot
// periodic methods (other than the scheduler calls).  Instead, the structure of the robot
// (including subsystems, commands, and button mappings) should be declared here.

public class RobotContainer {

  // Robot's Subsystems
  public static final DriveSubsystem m_robotDrive = new DriveSubsystem();

  // Creates Auto Selection Menu
  SendableChooser<Command> m_chooser = new SendableChooser<>();

  // Controllers
  CommandXboxController m_driverController = new CommandXboxController(ControllerConstants.kDriverControllerPort);

  // The container for the robot. Contains subsystems, OI devices, and commands.
  public RobotContainer() {

    // Configure the button bindings
    configureButtonBindings();

    // DEPRECATED: Calibrates Gyro
    m_robotDrive.calibrateGyro();

    // Configure Default Commands
    m_robotDrive.setDefaultCommand(
      
      // The left stick on the controller controls robot translation.
      // Turning is controlled by the X axis of the right stick.
      new RunCommand(

        // Joystick input to tele-op control
        () -> m_robotDrive.drive(
          -MathUtil.applyDeadband(-m_driverController.getLeftY(), ControllerConstants.kDriveDeadband),
          -MathUtil.applyDeadband(-m_driverController.getLeftX(), ControllerConstants.kDriveDeadband),
          -MathUtil.applyDeadband(m_driverController.getRightX(), ControllerConstants.kDriveDeadband),
          true, true), m_robotDrive
      )
    );

    // Put the auto chooser on Smart Dashboard
    SmartDashboard.putData("AutoMode", m_chooser);

    // Named Command Configuration
    // NamedCommands.registerCommand("Limelight Auto", limelightDrive);

    // Autos
    m_chooser.addOption("Horizontal", m_robotDrive.getAuto("Horizontal Auto"));
  }

  // Define your Button Bindings here
  private void configureButtonBindings() {

    // Zero Gyro - Start Button
    new JoystickButton(m_driverController.getHID(), ControllerConstants.k_start)
    .whileTrue(
      new InstantCommand(() -> m_robotDrive.calibrateGyro(), m_robotDrive)
    );

    // Sets wheels in X Position - A Button
    new JoystickButton(m_driverController.getHID(), ControllerConstants.k_A)
    .whileTrue(
      new RunCommand(() -> m_robotDrive.setX(), m_robotDrive)
    );
    
    // Goes to ID 6 -- Y Button
    new JoystickButton(m_driverController.getHID(), ControllerConstants.k_Y)
    .onTrue(
      new InstantCommand(() -> m_robotDrive.goToDesiredPose(LocationConstants.kReefID6),m_robotDrive)
    );
  }

  // Get Auto from menu yippee
  public Command getAutonomousCommand() {
    return m_chooser.getSelected();   
  } 
}