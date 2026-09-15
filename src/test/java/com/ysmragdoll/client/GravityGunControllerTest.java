package com.ysmragdoll.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class GravityGunControllerTest {
    @Test
    public void scrollDistanceStaysWithinReach() {
        assertEquals(1.5, GravityGunController.clampDistance(-100), 0);
        assertEquals(12, GravityGunController.clampDistance(100), 0);
        assertEquals(5.5, GravityGunController.clampDistance(5.5), 0);
    }
}
