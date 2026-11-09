
package airplane;

import static org.junit.jupiter.api.Assertions.assertThrows;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.models.FlightStatus;
import airplane.exceptions.FlightException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AircraftTest {

    @Test
    void prepareForTakeoff_whenAllConditionsAreValid_shouldBecomeReady() {
        // Arrange: Create mock dependencies
        Engine engine = mock(Engine.class);
        FuelSystem fuelSystem = mock(FuelSystem.class);
        LandingGear landingGear = mock(LandingGear.class);
        NavigationSystem navigationSystem = mock(NavigationSystem.class);

        // Stub the required conditions
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        Aircraft aircraft = new Aircraft(
                engine, fuelSystem, landingGear, navigationSystem
        );

        // Act
        aircraft.prepareForTakeoff();

        // Assert
        assertEquals(FlightStatus.READY, aircraft.getStatus());

        // Verify interactions
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();
    }

    @Test
    void prepareForTakeoff_whenRouteIsUnavailable_shouldFail() {
        // Arrange
        Engine engine = mock(Engine.class);
        FuelSystem fuelSystem = mock(FuelSystem.class);
        LandingGear landingGear = mock(LandingGear.class);
        NavigationSystem navigationSystem = mock(NavigationSystem.class);

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(false);

        Aircraft aircraft = new Aircraft(
                engine, fuelSystem, landingGear, navigationSystem
        );

        // Act & Assert
        FlightException exception = assertThrows(
                FlightException.class,
                () -> aircraft.prepareForTakeoff()
        );

        assertEquals(
                "Cannot prepare for takeoff: route is unavailable.",
                exception.getMessage()
        );

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        // Verify interactions
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();
        verifyNoInteractions(landingGear);
    }


    @Test
    void takeOff_whenAircraftIsReady_shouldStartEngineAndRetractGear() {
        // Arrange
        Engine engine = mock(Engine.class);
        FuelSystem fuelSystem = mock(FuelSystem.class);
        LandingGear landingGear = mock(LandingGear.class);
        NavigationSystem navigationSystem = mock(NavigationSystem.class);

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        Aircraft aircraft = new Aircraft(
                engine, fuelSystem, landingGear, navigationSystem
        );

        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());

        // Act
        aircraft.takeOff();

        // Assert
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        // Verify interactions
        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();
    }


    @Test
    void takeOff_whenEngineStartFails_shouldAbort() {
        // Arrange
        Engine engine = mock(Engine.class);
        FuelSystem fuelSystem = mock(FuelSystem.class);
        LandingGear landingGear = mock(LandingGear.class);
        NavigationSystem navigationSystem = mock(NavigationSystem.class);

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        Aircraft aircraft = new Aircraft(
                engine, fuelSystem, landingGear, navigationSystem
        );

        aircraft.prepareForTakeoff();

        // Simulate engine failure during takeoff
        doThrow(new IllegalStateException("Starter failure"))
                .when(engine).start();

        // Act
        FlightException exception = assertThrows(
                FlightException.class,
                aircraft::takeOff
        );

        // Assert
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        assertEquals("Takeoff failed.", exception.getMessage());
        assertEquals(
                "Starter failure",
                exception.getCause().getMessage()
        );

        // Verify interactions
        verify(engine, times(1)).start();
        verifyNoInteractions(landingGear);
    }


    @Test
    void cruise_whenEngineFails_shouldEmergencyLand() {
        // Arrange
        Engine engine = mock(Engine.class);
        FuelSystem fuelSystem = mock(FuelSystem.class);
        LandingGear landingGear = mock(LandingGear.class);
        NavigationSystem navigationSystem = mock(NavigationSystem.class);

        // Healthy during preparation, failed during cruise
        when(engine.isOperational()).thenReturn(true, false);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        // Explicitly configure the void method on this mock
        doNothing().when(landingGear).deploy();

        Aircraft aircraft = new Aircraft(
                engine, fuelSystem, landingGear, navigationSystem
        );

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        // Act: Detect engine failure during flight
        aircraft.cruise(10.0);

        // Assert: Emergency landing is completed
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        // Verify the interactions
        verify(engine, times(2)).isOperational();
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();

        // No fuel should be consumed after detecting engine failure
        verify(fuelSystem, never()).consumeFuel(anyDouble());
    }


    @Test
    void cruise_whenFuelConsumptionFails_shouldEmergencyLand() {
        // Arrange
        Engine engine = mock(Engine.class);
        FuelSystem fuelSystem = mock(FuelSystem.class);
        LandingGear landingGear = mock(LandingGear.class);
        NavigationSystem navigationSystem = mock(NavigationSystem.class);

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        Aircraft aircraft = new Aircraft(
                engine, fuelSystem, landingGear, navigationSystem
        );

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        // Simulate a fuel system failure
        doThrow(new IllegalStateException("Fuel pump failure"))
                .when(fuelSystem).consumeFuel(10.0);

        // Act
        FlightException exception = assertThrows(
                FlightException.class,
                () -> aircraft.cruise(10.0)
        );

        // Assert
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        assertEquals(
                "A problem occurred while consuming fuel.",
                exception.getMessage()
        );

        assertEquals(
                "Fuel pump failure",
                exception.getCause().getMessage()
        );

        // Verify interactions
        verify(fuelSystem, times(1)).consumeFuel(10.0);
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }


    @Test
    void prepareForTakeoff_whenEngineCheckThrows_shouldRemainGrounded() {
        // Arrange
        Engine engine = mock(Engine.class);
        FuelSystem fuelSystem = mock(FuelSystem.class);
        LandingGear landingGear = mock(LandingGear.class);
        NavigationSystem navigationSystem = mock(NavigationSystem.class);

        when(engine.isOperational())
                .thenThrow(new IllegalStateException("Engine diagnostics failed"));

        Aircraft aircraft = new Aircraft(
                engine, fuelSystem, landingGear, navigationSystem
        );

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                aircraft::prepareForTakeoff
        );

        assertEquals("Engine diagnostics failed", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        // Verify that later dependency checks were not reached
        verify(engine, times(1)).isOperational();
        verifyNoInteractions(fuelSystem, navigationSystem, landingGear);
    }


    @Test
    void prepareForTakeoff_afterEngineRecovery_shouldAllowFlight() {
        // Arrange
        Engine engine = mock(Engine.class);
        FuelSystem fuelSystem = mock(FuelSystem.class);
        LandingGear landingGear = mock(LandingGear.class);
        NavigationSystem navigationSystem = mock(NavigationSystem.class);

        // Initially, the engine is not operational
        when(engine.isOperational()).thenReturn(false);

        Aircraft aircraft = new Aircraft(
                engine, fuelSystem, landingGear, navigationSystem
        );

        // First attempt must fail
        FlightException exception = assertThrows(
                FlightException.class,
                aircraft::prepareForTakeoff
        );

        assertEquals(
                "Cannot prepare for takeoff: engine is not operational.",
                exception.getMessage()
        );
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, times(1)).isOperational();

        // Reset the mock to simulate a fresh engine configuration
        reset(engine);

        // Configure the recovered engine and other dependencies
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        // Second attempt: complete a normal flight
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        aircraft.cruise(7.5);
        aircraft.land();

        // Assert final status
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        // Verify interactions after reset
        verify(engine, times(2)).isOperational();
        verify(engine, times(1)).start();
        verify(engine, times(1)).stop();

        verify(landingGear, times(1)).retract();
        verify(landingGear, times(1)).deploy();

        // Type-specific any() matcher
        verify(fuelSystem, times(1)).consumeFuel(any(Double.class));

        // Also verify the exact fuel amount
        verify(fuelSystem, times(1)).consumeFuel(7.5);
    }


    @Test
    void takeOff_whenNotPrepared_shouldRejectOperation() {
        // Arrange
        Engine engine = mock(Engine.class);
        FuelSystem fuelSystem = mock(FuelSystem.class);
        LandingGear landingGear = mock(LandingGear.class);
        NavigationSystem navigationSystem = mock(NavigationSystem.class);

        Aircraft aircraft = new Aircraft(
                engine, fuelSystem, landingGear, navigationSystem
        );

        // Act & Assert
        FlightException exception = assertThrows(
                FlightException.class,
                aircraft::takeOff
        );

        assertEquals(
                "Operation is not allowed while aircraft status is GROUNDED.",
                exception.getMessage()
        );
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        // No component should be used during an invalid takeoff
        verifyNoInteractions(
                engine, fuelSystem, landingGear, navigationSystem
        );
    }

    @Test
    void prepareForTakeoff_whenFuelInsufficient_shouldRemainGrounded() {
        // Arrange
        Engine engine = mock(Engine.class);
        FuelSystem fuelSystem = mock(FuelSystem.class);
        LandingGear landingGear = mock(LandingGear.class);
        NavigationSystem navigationSystem = mock(NavigationSystem.class);

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(false);

        Aircraft aircraft = new Aircraft(
                engine, fuelSystem, landingGear, navigationSystem
        );

        // Act & Assert
        FlightException exception = assertThrows(
                FlightException.class,
                aircraft::prepareForTakeoff
        );

        assertEquals(
                "Cannot prepare for takeoff: insufficient fuel.",
                exception.getMessage()
        );
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        // Verify checks before the failure
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();

        // Navigation and landing gear should not be accessed
        verifyNoInteractions(navigationSystem, landingGear);
    }

}
