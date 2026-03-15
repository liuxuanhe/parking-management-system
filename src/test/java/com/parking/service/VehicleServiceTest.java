package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.dto.VehicleAddRequest;
import com.parking.dto.VehicleAddResponse;
import com.parking.dto.VehicleQueryResponse;
import com.parking.mapper.CarPlateMapper;
import com.parking.mapper.OwnerMapper;
import com.parking.model.CarPlate;
import com.parking.model.Owner;
import com.parking.service.impl.VehicleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VehicleService 单元测试
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.9
 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private CarPlateMapper carPlateMapper;

    @Mock
    private OwnerMapper ownerMapper;

    @Mock
    private CacheService cacheService;

    @Mock
    private MaskingService maskingService;

    @Mock
    private DistributedLockService distributedLockService;

    private VehicleServiceImpl vehicleService;

    private static final Long COMMUNITY_ID = 1001L;
    private static final String HOUSE_NO = "1-101";
    private static final Long OWNER_ID = 10001L;
    private static final String VALID_CAR_NUMBER = "京A12345";

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleServiceImpl(carPlateMapper, ownerMapper, cacheService, maskingService, distributedLockService);
    }

    private VehicleAddRequest createValidRequest() {
        VehicleAddRequest request = new VehicleAddRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setHouseNo(HOUSE_NO);
        request.setOwnerId(OWNER_ID);
        request.setCarNumber(VALID_CAR_NUMBER);
        request.setCarBrand("宝马");
        request.setCarModel("3系");
        request.setCarColor("白色");
        return request;
    }

    @Nested
    @DisplayName("addVehicle - 添加车牌")
    class AddVehicleTests {

        @Test
        @DisplayName("添加车牌成功应创建 normal 状态的车牌记录")
        void addVehicle_success_shouldCreateNormalCarPlate() {
            VehicleAddRequest request = createValidRequest();
            when(carPlateMapper.countByOwner(COMMUNITY_ID, HOUSE_NO, OWNER_ID)).thenReturn(0);
            when(carPlateMapper.countByCarNumberInCommunity(COMMUNITY_ID, VALID_CAR_NUMBER, OWNER_ID)).thenReturn(0);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");
            doAnswer(invocation -> {
                CarPlate carPlate = invocation.getArgument(0);
                carPlate.setId(1L);
                return null;
            }).when(carPlateMapper).insert(any(CarPlate.class));

            VehicleAddResponse response = vehicleService.addVehicle(request);

            assertNotNull(response);
            assertEquals(1L, response.getVehicleId());
            assertEquals(VALID_CAR_NUMBER, response.getCarNumber());
            assertEquals("normal", response.getStatus());
        }

        @Test
        @DisplayName("添加车牌成功应正确设置所有字段")
        void addVehicle_success_shouldSetAllFields() {
            VehicleAddRequest request = createValidRequest();
            when(carPlateMapper.countByOwner(COMMUNITY_ID, HOUSE_NO, OWNER_ID)).thenReturn(0);
            when(carPlateMapper.countByCarNumberInCommunity(COMMUNITY_ID, VALID_CAR_NUMBER, OWNER_ID)).thenReturn(0);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");
            doAnswer(invocation -> {
                CarPlate carPlate = invocation.getArgument(0);
                carPlate.setId(1L);
                return null;
            }).when(carPlateMapper).insert(any(CarPlate.class));

            vehicleService.addVehicle(request);

            ArgumentCaptor<CarPlate> captor = ArgumentCaptor.forClass(CarPlate.class);
            verify(carPlateMapper).insert(captor.capture());
            CarPlate captured = captor.getValue();
            assertEquals(COMMUNITY_ID, captured.getCommunityId());
            assertEquals(HOUSE_NO, captured.getHouseNo());
            assertEquals(OWNER_ID, captured.getOwnerId());
            assertEquals(VALID_CAR_NUMBER, captured.getCarNumber());
            assertEquals("宝马", captured.getCarBrand());
            assertEquals("3系", captured.getCarModel());
            assertEquals("白色", captured.getCarColor());
            assertEquals("normal", captured.getStatus());
        }

        @Test
        @DisplayName("添加车牌成功应失效缓存")
        void addVehicle_success_shouldInvalidateCache() {
            VehicleAddRequest request = createValidRequest();
            when(carPlateMapper.countByOwner(COMMUNITY_ID, HOUSE_NO, OWNER_ID)).thenReturn(0);
            when(carPlateMapper.countByCarNumberInCommunity(COMMUNITY_ID, VALID_CAR_NUMBER, OWNER_ID)).thenReturn(0);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");
            doAnswer(invocation -> {
                CarPlate carPlate = invocation.getArgument(0);
                carPlate.setId(1L);
                return null;
            }).when(carPlateMapper).insert(any(CarPlate.class));

            vehicleService.addVehicle(request);

            verify(cacheService).generateKey("vehicles", COMMUNITY_ID, HOUSE_NO);
            verify(cacheService).delete("vehicles:1001:1-101");
        }

        @Test
        @DisplayName("车牌格式无效应抛出 PARAM_ERROR 异常")
        void addVehicle_invalidCarNumber_shouldThrowException() {
            VehicleAddRequest request = createValidRequest();
            request.setCarNumber("INVALID");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> vehicleService.addVehicle(request));

            assertEquals(10000, exception.getCode());
            verify(carPlateMapper, never()).insert(any());
        }

        @Test
        @DisplayName("车牌数量达到上限（5个）应抛出 PARKING_3001 异常")
        void addVehicle_maxCarPlates_shouldThrowParking3001() {
            VehicleAddRequest request = createValidRequest();
            when(carPlateMapper.countByOwner(COMMUNITY_ID, HOUSE_NO, OWNER_ID)).thenReturn(5);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> vehicleService.addVehicle(request));

            assertEquals(3001, exception.getCode());
            verify(carPlateMapper, never()).insert(any());
        }

        @Test
        @DisplayName("车牌已有4个时仍可添加第5个")
        void addVehicle_fourExisting_shouldAllowFifth() {
            VehicleAddRequest request = createValidRequest();
            when(carPlateMapper.countByOwner(COMMUNITY_ID, HOUSE_NO, OWNER_ID)).thenReturn(4);
            when(carPlateMapper.countByCarNumberInCommunity(COMMUNITY_ID, VALID_CAR_NUMBER, OWNER_ID)).thenReturn(0);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");
            doAnswer(invocation -> {
                CarPlate carPlate = invocation.getArgument(0);
                carPlate.setId(5L);
                return null;
            }).when(carPlateMapper).insert(any(CarPlate.class));

            VehicleAddResponse response = vehicleService.addVehicle(request);

            assertNotNull(response);
            assertEquals(5L, response.getVehicleId());
        }

        @Test
        @DisplayName("车牌在小区内已被其他业主绑定应抛出异常")
        void addVehicle_carNumberBoundByOther_shouldThrowException() {
            VehicleAddRequest request = createValidRequest();
            when(carPlateMapper.countByOwner(COMMUNITY_ID, HOUSE_NO, OWNER_ID)).thenReturn(0);
            when(carPlateMapper.countByCarNumberInCommunity(COMMUNITY_ID, VALID_CAR_NUMBER, OWNER_ID)).thenReturn(1);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> vehicleService.addVehicle(request));

            assertTrue(exception.getMessage().contains("已被其他业主绑定"));
            verify(carPlateMapper, never()).insert(any());
        }

        @Test
        @DisplayName("新能源车牌格式应被接受")
        void addVehicle_newEnergyPlate_shouldBeAccepted() {
            VehicleAddRequest request = createValidRequest();
            request.setCarNumber("京AD12345");
            when(carPlateMapper.countByOwner(COMMUNITY_ID, HOUSE_NO, OWNER_ID)).thenReturn(0);
            when(carPlateMapper.countByCarNumberInCommunity(COMMUNITY_ID, "京AD12345", OWNER_ID)).thenReturn(0);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");
            doAnswer(invocation -> {
                CarPlate carPlate = invocation.getArgument(0);
                carPlate.setId(1L);
                return null;
            }).when(carPlateMapper).insert(any(CarPlate.class));

            VehicleAddResponse response = vehicleService.addVehicle(request);

            assertNotNull(response);
            assertEquals("京AD12345", response.getCarNumber());
        }
    }

    @Nested
    @DisplayName("deleteVehicle - 删除车牌")
    class DeleteVehicleTests {

        private CarPlate createNormalCarPlate() {
            CarPlate carPlate = new CarPlate();
            carPlate.setId(100L);
            carPlate.setCommunityId(COMMUNITY_ID);
            carPlate.setHouseNo(HOUSE_NO);
            carPlate.setOwnerId(OWNER_ID);
            carPlate.setCarNumber(VALID_CAR_NUMBER);
            carPlate.setStatus("normal");
            carPlate.setIsDeleted(0);
            return carPlate;
        }

        @Test
        @DisplayName("删除 normal 状态车牌应成功执行逻辑删除")
        void deleteVehicle_normalStatus_shouldLogicalDelete() {
            CarPlate carPlate = createNormalCarPlate();
            when(carPlateMapper.selectById(100L)).thenReturn(carPlate);
            when(carPlateMapper.logicalDelete(100L)).thenReturn(1);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");

            vehicleService.deleteVehicle(100L);

            verify(carPlateMapper).logicalDelete(100L);
        }

        @Test
        @DisplayName("删除车牌成功应失效缓存")
        void deleteVehicle_success_shouldInvalidateCache() {
            CarPlate carPlate = createNormalCarPlate();
            when(carPlateMapper.selectById(100L)).thenReturn(carPlate);
            when(carPlateMapper.logicalDelete(100L)).thenReturn(1);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");

            vehicleService.deleteVehicle(100L);

            verify(cacheService).generateKey("vehicles", COMMUNITY_ID, HOUSE_NO);
            verify(cacheService).delete("vehicles:1001:1-101");
        }

        @Test
        @DisplayName("车牌记录不存在应抛出 PARAM_ERROR 异常")
        void deleteVehicle_notFound_shouldThrowException() {
            when(carPlateMapper.selectById(999L)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> vehicleService.deleteVehicle(999L));

            assertEquals(10000, exception.getCode());
            assertTrue(exception.getMessage().contains("车牌记录不存在"));
            verify(carPlateMapper, never()).logicalDelete(anyLong());
        }

        @Test
        @DisplayName("车辆在场（status=entered）应抛出 PARKING_3002 异常")
        void deleteVehicle_vehicleEntered_shouldThrowParking3002() {
            CarPlate carPlate = createNormalCarPlate();
            carPlate.setStatus("entered");
            when(carPlateMapper.selectById(100L)).thenReturn(carPlate);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> vehicleService.deleteVehicle(100L));

            assertEquals(3002, exception.getCode());
            verify(carPlateMapper, never()).logicalDelete(anyLong());
        }

        @Test
        @DisplayName("删除 primary 状态车牌应成功")
        void deleteVehicle_primaryStatus_shouldSucceed() {
            CarPlate carPlate = createNormalCarPlate();
            carPlate.setStatus("primary");
            when(carPlateMapper.selectById(100L)).thenReturn(carPlate);
            when(carPlateMapper.logicalDelete(100L)).thenReturn(1);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");

            vehicleService.deleteVehicle(100L);

            verify(carPlateMapper).logicalDelete(100L);
        }

        @Test
        @DisplayName("逻辑删除返回0行应抛出异常（并发删除场景）")
        void deleteVehicle_concurrentDelete_shouldThrowException() {
            CarPlate carPlate = createNormalCarPlate();
            when(carPlateMapper.selectById(100L)).thenReturn(carPlate);
            when(carPlateMapper.logicalDelete(100L)).thenReturn(0);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> vehicleService.deleteVehicle(100L));

            assertEquals(10000, exception.getCode());
            assertTrue(exception.getMessage().contains("车牌删除失败"));
        }
    }

    @Nested
    @DisplayName("listVehicles - 查询车牌列表")
    class ListVehiclesTests {

        private CarPlate createCarPlate(Long id, Long ownerId, String carNumber, String status) {
            CarPlate cp = new CarPlate();
            cp.setId(id);
            cp.setCommunityId(COMMUNITY_ID);
            cp.setHouseNo(HOUSE_NO);
            cp.setOwnerId(ownerId);
            cp.setCarNumber(carNumber);
            cp.setCarBrand("宝马");
            cp.setCarModel("3系");
            cp.setCarColor("白色");
            cp.setStatus(status);
            cp.setCreateTime(LocalDateTime.of(2024, 1, 15, 10, 0, 0));
            return cp;
        }

        private Owner createOwner(Long id, String phone) {
            Owner owner = new Owner();
            owner.setId(id);
            owner.setPhoneNumber(phone);
            return owner;
        }

        @Test
        @DisplayName("缓存命中时应直接返回缓存结果，不查询数据库")
        void listVehicles_cacheHit_shouldReturnCachedResult() {
            String cacheKey = "vehicles:1001:1-101";
            VehicleQueryResponse cachedResponse = new VehicleQueryResponse();
            cachedResponse.setVehicles(Collections.emptyList());
            cachedResponse.setTotal(0);

            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.of(cachedResponse));

            VehicleQueryResponse result = vehicleService.listVehicles(COMMUNITY_ID, HOUSE_NO);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
            verify(carPlateMapper, never()).selectByHouse(anyLong(), anyString());
        }

        @Test
        @DisplayName("缓存未命中时应查询数据库并写入缓存")
        void listVehicles_cacheMiss_shouldQueryDbAndSetCache() {
            String cacheKey = "vehicles:1001:1-101";
            CarPlate cp = createCarPlate(1L, OWNER_ID, VALID_CAR_NUMBER, "normal");
            Owner owner = createOwner(OWNER_ID, "13812345678");

            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.empty());
            when(carPlateMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(List.of(cp));
            when(ownerMapper.selectById(OWNER_ID)).thenReturn(owner);
            when(maskingService.maskPhoneNumber("13812345678")).thenReturn("138****5678");

            VehicleQueryResponse result = vehicleService.listVehicles(COMMUNITY_ID, HOUSE_NO);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            verify(cacheService).set(eq(cacheKey), any(VehicleQueryResponse.class), eq(30L), any());
        }

        @Test
        @DisplayName("应对车主手机号执行脱敏处理")
        void listVehicles_shouldMaskOwnerPhone() {
            String cacheKey = "vehicles:1001:1-101";
            CarPlate cp = createCarPlate(1L, OWNER_ID, VALID_CAR_NUMBER, "normal");
            Owner owner = createOwner(OWNER_ID, "13812345678");

            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.empty());
            when(carPlateMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(List.of(cp));
            when(ownerMapper.selectById(OWNER_ID)).thenReturn(owner);
            when(maskingService.maskPhoneNumber("13812345678")).thenReturn("138****5678");

            VehicleQueryResponse result = vehicleService.listVehicles(COMMUNITY_ID, HOUSE_NO);

            assertEquals("138****5678", result.getVehicles().get(0).getOwnerPhone());
            verify(maskingService).maskPhoneNumber("13812345678");
        }

        @Test
        @DisplayName("同房屋号多业主场景应返回所有业主的车牌")
        void listVehicles_multipleOwners_shouldReturnAllVehicles() {
            String cacheKey = "vehicles:1001:1-101";
            Long owner2Id = 10002L;
            CarPlate cp1 = createCarPlate(1L, OWNER_ID, "京A12345", "primary");
            CarPlate cp2 = createCarPlate(2L, OWNER_ID, "京B67890", "normal");
            CarPlate cp3 = createCarPlate(3L, owner2Id, "京C11111", "normal");

            Owner owner1 = createOwner(OWNER_ID, "13812345678");
            Owner owner2 = createOwner(owner2Id, "13999998888");

            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.empty());
            when(carPlateMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(List.of(cp1, cp2, cp3));
            when(ownerMapper.selectById(OWNER_ID)).thenReturn(owner1);
            when(ownerMapper.selectById(owner2Id)).thenReturn(owner2);
            when(maskingService.maskPhoneNumber("13812345678")).thenReturn("138****5678");
            when(maskingService.maskPhoneNumber("13999998888")).thenReturn("139****8888");

            VehicleQueryResponse result = vehicleService.listVehicles(COMMUNITY_ID, HOUSE_NO);

            assertEquals(3, result.getTotal());
            assertEquals(3, result.getVehicles().size());
            // 验证不同业主的车牌都包含在内
            assertEquals("京A12345", result.getVehicles().get(0).getCarNumber());
            assertEquals("京C11111", result.getVehicles().get(2).getCarNumber());
            assertEquals("139****8888", result.getVehicles().get(2).getOwnerPhone());
        }

        @Test
        @DisplayName("无车牌时应返回空列表")
        void listVehicles_noVehicles_shouldReturnEmptyList() {
            String cacheKey = "vehicles:1001:1-101";

            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.empty());
            when(carPlateMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(Collections.emptyList());

            VehicleQueryResponse result = vehicleService.listVehicles(COMMUNITY_ID, HOUSE_NO);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
            assertTrue(result.getVehicles().isEmpty());
            verify(cacheService).set(eq(cacheKey), any(VehicleQueryResponse.class), eq(30L), any());
        }

        @Test
        @DisplayName("业主不存在时手机号应为 null")
        void listVehicles_ownerNotFound_shouldHaveNullPhone() {
            String cacheKey = "vehicles:1001:1-101";
            CarPlate cp = createCarPlate(1L, OWNER_ID, VALID_CAR_NUMBER, "normal");

            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.empty());
            when(carPlateMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(List.of(cp));
            when(ownerMapper.selectById(OWNER_ID)).thenReturn(null);

            VehicleQueryResponse result = vehicleService.listVehicles(COMMUNITY_ID, HOUSE_NO);

            assertNull(result.getVehicles().get(0).getOwnerPhone());
            verify(maskingService, never()).maskPhoneNumber(anyString());
        }

        @Test
        @DisplayName("应正确映射车牌实体字段到响应 DTO")
        void listVehicles_shouldMapFieldsCorrectly() {
            String cacheKey = "vehicles:1001:1-101";
            CarPlate cp = createCarPlate(99L, OWNER_ID, "京AD12345", "primary");
            cp.setCarBrand("特斯拉");
            cp.setCarModel("Model 3");
            cp.setCarColor("黑色");
            Owner owner = createOwner(OWNER_ID, "13812345678");

            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.empty());
            when(carPlateMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(List.of(cp));
            when(ownerMapper.selectById(OWNER_ID)).thenReturn(owner);
            when(maskingService.maskPhoneNumber("13812345678")).thenReturn("138****5678");

            VehicleQueryResponse result = vehicleService.listVehicles(COMMUNITY_ID, HOUSE_NO);

            VehicleQueryResponse.VehicleItem item = result.getVehicles().get(0);
            assertEquals(99L, item.getVehicleId());
            assertEquals("京AD12345", item.getCarNumber());
            assertEquals("特斯拉", item.getCarBrand());
            assertEquals("Model 3", item.getCarModel());
            assertEquals("黑色", item.getCarColor());
            assertEquals("primary", item.getStatus());
            assertEquals(OWNER_ID, item.getOwnerId());
            assertNotNull(item.getCreateTime());
        }
    }

    @Nested
    @DisplayName("setPrimaryVehicle - 设置 Primary 车辆")
    class SetPrimaryTests {

        private static final Long VEHICLE_ID = 20001L;
        private static final Long OTHER_VEHICLE_ID = 20002L;

        private CarPlate createCarPlateForPrimary(Long id, String status) {
            CarPlate cp = new CarPlate();
            cp.setId(id);
            cp.setCommunityId(COMMUNITY_ID);
            cp.setHouseNo(HOUSE_NO);
            cp.setOwnerId(OWNER_ID);
            cp.setCarNumber("京A" + id);
            cp.setStatus(status);
            cp.setIsDeleted(0);
            cp.setCreateTime(LocalDateTime.now());
            return cp;
        }

        /**
         * 模拟 executeWithLock：直接执行传入的 Supplier
         */
        private void mockLockExecution() {
            when(distributedLockService.executeWithLock(anyString(), any()))
                    .thenAnswer(invocation -> {
                        java.util.function.Supplier<?> supplier = invocation.getArgument(1);
                        return supplier.get();
                    });
        }

        @Test
        @DisplayName("正常切换 Primary 车辆：旧 Primary 变 normal，新车辆变 primary")
        void setPrimary_success() {
            // 准备数据：一辆 primary，一辆 normal
            CarPlate primaryCar = createCarPlateForPrimary(OTHER_VEHICLE_ID, "primary");
            CarPlate normalCar = createCarPlateForPrimary(VEHICLE_ID, "normal");
            List<CarPlate> vehicles = List.of(primaryCar, normalCar);

            mockLockExecution();
            when(carPlateMapper.selectForUpdate(COMMUNITY_ID, HOUSE_NO)).thenReturn(vehicles);
            when(carPlateMapper.updatePrimaryToNormal(COMMUNITY_ID, HOUSE_NO)).thenReturn(1);
            when(carPlateMapper.updateStatusToPrimary(VEHICLE_ID)).thenReturn(1);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");

            vehicleService.setPrimaryVehicle(VEHICLE_ID, COMMUNITY_ID, HOUSE_NO);

            verify(carPlateMapper).selectForUpdate(COMMUNITY_ID, HOUSE_NO);
            verify(carPlateMapper).updatePrimaryToNormal(COMMUNITY_ID, HOUSE_NO);
            verify(carPlateMapper).updateStatusToPrimary(VEHICLE_ID);
            verify(cacheService).delete("vehicles:1001:1-101");
        }

        @Test
        @DisplayName("目标车辆已是 Primary，幂等返回不执行更新")
        void setPrimary_alreadyPrimary_idempotent() {
            CarPlate primaryCar = createCarPlateForPrimary(VEHICLE_ID, "primary");
            List<CarPlate> vehicles = List.of(primaryCar);

            mockLockExecution();
            when(carPlateMapper.selectForUpdate(COMMUNITY_ID, HOUSE_NO)).thenReturn(vehicles);

            vehicleService.setPrimaryVehicle(VEHICLE_ID, COMMUNITY_ID, HOUSE_NO);

            // 不应执行任何更新操作
            verify(carPlateMapper, never()).updatePrimaryToNormal(anyLong(), anyString());
            verify(carPlateMapper, never()).updateStatusToPrimary(anyLong());
        }

        @Test
        @DisplayName("房屋号下有车辆在场（status=entered），拒绝切换并返回 PARKING_4001")
        void setPrimary_vehicleInPark_throwsError() {
            CarPlate enteredCar = createCarPlateForPrimary(OTHER_VEHICLE_ID, "entered");
            CarPlate normalCar = createCarPlateForPrimary(VEHICLE_ID, "normal");
            List<CarPlate> vehicles = List.of(enteredCar, normalCar);

            mockLockExecution();
            when(carPlateMapper.selectForUpdate(COMMUNITY_ID, HOUSE_NO)).thenReturn(vehicles);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> vehicleService.setPrimaryVehicle(VEHICLE_ID, COMMUNITY_ID, HOUSE_NO));
            assertEquals(4001, ex.getCode());

            verify(carPlateMapper, never()).updatePrimaryToNormal(anyLong(), anyString());
            verify(carPlateMapper, never()).updateStatusToPrimary(anyLong());
        }

        @Test
        @DisplayName("目标车辆不存在或不属于该房屋号，抛出参数错误")
        void setPrimary_vehicleNotFound_throwsError() {
            // 房屋号下只有一辆车，但不是目标 vehicleId
            CarPlate otherCar = createCarPlateForPrimary(OTHER_VEHICLE_ID, "normal");
            List<CarPlate> vehicles = List.of(otherCar);

            mockLockExecution();
            when(carPlateMapper.selectForUpdate(COMMUNITY_ID, HOUSE_NO)).thenReturn(vehicles);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> vehicleService.setPrimaryVehicle(VEHICLE_ID, COMMUNITY_ID, HOUSE_NO));
            assertEquals(10000, ex.getCode()); // PARAM_ERROR
        }

        @Test
        @DisplayName("房屋号下无车牌记录，抛出参数错误")
        void setPrimary_noVehicles_throwsError() {
            mockLockExecution();
            when(carPlateMapper.selectForUpdate(COMMUNITY_ID, HOUSE_NO)).thenReturn(Collections.emptyList());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> vehicleService.setPrimaryVehicle(VEHICLE_ID, COMMUNITY_ID, HOUSE_NO));
            assertEquals(10000, ex.getCode()); // PARAM_ERROR
        }

        @Test
        @DisplayName("首次设置 Primary（之前无 Primary 车辆），直接设置成功")
        void setPrimary_noPreviousPrimary_success() {
            CarPlate normalCar1 = createCarPlateForPrimary(VEHICLE_ID, "normal");
            CarPlate normalCar2 = createCarPlateForPrimary(OTHER_VEHICLE_ID, "normal");
            List<CarPlate> vehicles = List.of(normalCar1, normalCar2);

            mockLockExecution();
            when(carPlateMapper.selectForUpdate(COMMUNITY_ID, HOUSE_NO)).thenReturn(vehicles);
            when(carPlateMapper.updatePrimaryToNormal(COMMUNITY_ID, HOUSE_NO)).thenReturn(0); // 无旧 Primary
            when(carPlateMapper.updateStatusToPrimary(VEHICLE_ID)).thenReturn(1);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");

            vehicleService.setPrimaryVehicle(VEHICLE_ID, COMMUNITY_ID, HOUSE_NO);

            verify(carPlateMapper).updateStatusToPrimary(VEHICLE_ID);
            verify(cacheService).delete("vehicles:1001:1-101");
        }

        @Test
        @DisplayName("分布式锁正确获取和释放")
        void setPrimary_distributedLockUsed() {
            CarPlate normalCar = createCarPlateForPrimary(VEHICLE_ID, "normal");
            List<CarPlate> vehicles = List.of(normalCar);

            mockLockExecution();
            when(carPlateMapper.selectForUpdate(COMMUNITY_ID, HOUSE_NO)).thenReturn(vehicles);
            when(carPlateMapper.updatePrimaryToNormal(COMMUNITY_ID, HOUSE_NO)).thenReturn(0);
            when(carPlateMapper.updateStatusToPrimary(VEHICLE_ID)).thenReturn(1);
            when(cacheService.generateKey("vehicles", COMMUNITY_ID, HOUSE_NO)).thenReturn("vehicles:1001:1-101");

            vehicleService.setPrimaryVehicle(VEHICLE_ID, COMMUNITY_ID, HOUSE_NO);

            // 验证分布式锁被调用，锁键格式正确
            verify(distributedLockService).executeWithLock(
                    eq("lock:primary:" + COMMUNITY_ID + ":" + HOUSE_NO), any());
        }

        @Test
        @DisplayName("updateStatusToPrimary 返回0行（更新失败），抛出异常")
        void setPrimary_updateFails_throwsError() {
            CarPlate normalCar = createCarPlateForPrimary(VEHICLE_ID, "normal");
            List<CarPlate> vehicles = List.of(normalCar);

            mockLockExecution();
            when(carPlateMapper.selectForUpdate(COMMUNITY_ID, HOUSE_NO)).thenReturn(vehicles);
            when(carPlateMapper.updatePrimaryToNormal(COMMUNITY_ID, HOUSE_NO)).thenReturn(0);
            when(carPlateMapper.updateStatusToPrimary(VEHICLE_ID)).thenReturn(0); // 更新失败

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> vehicleService.setPrimaryVehicle(VEHICLE_ID, COMMUNITY_ID, HOUSE_NO));
            assertEquals(10000, ex.getCode()); // PARAM_ERROR
        }
    }
}
