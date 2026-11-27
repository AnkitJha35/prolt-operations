package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.BaseIntegrationTest;
import com.sense.prolt_operations.dto.calculator.ExchangeRateSlotProjection;
import com.sense.prolt_operations.entity.*;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SavingCalculatorRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ExchangeDamRateRepository exchangeDamRateRepository;

    @Autowired
    private ExchangeRtmRateRepository exchangeRtmRateRepository;

    @Autowired
    private ExchangeGdamRateRepository exchangeGdamRateRepository;

    @Autowired
    private RegionStateRepository regionStateRepository;

    @Autowired
    private StateTariffRepository stateTariffRepository;

    @Autowired
    private StateChargesRepository stateChargesRepository;

    @Autowired
    private IstsChargesRepository istsChargesRepository;

    @Autowired
    private CtuChargesRepository ctuChargesRepository;

    @Autowired
    private IexFeesRepository iexFeesRepository;

    @Autowired
    private FppaChargesRepository fppaChargesRepository;

    @Autowired
    private SavingsCalculatorDataAccessService dataAccessService;

    private static final String STATE_CODE = "TEST_UP";
    private static final String STATE_NAME = "Test Uttar Pradesh";
    private static final LocalDate SIM_DATE = LocalDate.of(2024, 5, 1);

    @BeforeEach
    void setUpTestData() {
        // 1. Region State
        RegionState regionState = RegionState.create();
        regionState.setStateCode(STATE_CODE);
        regionState.setStateName(STATE_NAME);
        regionState.setRegionCode("NR");
        regionState.setRegionName("Northern Region");
        regionState.setRegionalGrid("NEW");
        regionState.setUpdatedAt(LocalDateTime.now());
        regionStateRepository.save(regionState);

        // 2. Exchange Rates for slot 1 (00:00:00) and slot 2 (00:15:00)
        ExchangeDamRate dam1 = ExchangeDamRate.create();
        dam1.setDate(SIM_DATE);
        dam1.setIntervalTime(LocalTime.of(0, 0, 0));
        dam1.setState(STATE_CODE);
        dam1.setMcp(new BigDecimal("4500.5000"));
        dam1.setUpdatedAt(OffsetDateTime.now());
        exchangeDamRateRepository.save(dam1);

        ExchangeRtmRate rtm1 = ExchangeRtmRate.create();
        rtm1.setDate(SIM_DATE);
        rtm1.setIntervalTime(LocalTime.of(0, 0, 0));
        rtm1.setState(STATE_CODE);
        rtm1.setMcp(new BigDecimal("4200.2500"));
        rtm1.setUpdatedAt(OffsetDateTime.now());
        exchangeRtmRateRepository.save(rtm1);

        ExchangeGdamRate gdam1 = ExchangeGdamRate.create();
        gdam1.setDate(SIM_DATE);
        gdam1.setIntervalTime(LocalTime.of(0, 0, 0));
        gdam1.setState(STATE_CODE);
        gdam1.setMcp(new BigDecimal("4800.7500"));
        gdam1.setUpdatedAt(OffsetDateTime.now());
        exchangeGdamRateRepository.save(gdam1);

        ExchangeDamRate dam2 = ExchangeDamRate.create();
        dam2.setDate(SIM_DATE);
        dam2.setIntervalTime(LocalTime.of(0, 15, 0));
        dam2.setState(STATE_CODE);
        dam2.setMcp(new BigDecimal("5100.0000"));
        dam2.setUpdatedAt(OffsetDateTime.now());
        exchangeDamRateRepository.save(dam2);

        // 3. State Tariff
        StateTariff tariff = StateTariff.create();
        tariff.setState(STATE_NAME);
        tariff.setConsumerCategory("HV-2");
        tariff.setSubCategory("General");
        tariff.setSupplyVoltageCategory("11 kV");
        tariff.setSupplyVoltage("11 kV");
        tariff.setMonth(202405);
        tariff.setConsumptionMonth(202405);
        tariff.setEnergyRate(new BigDecimal("6.50"));
        tariff.setBaseEnergyRate(new BigDecimal("6.00"));
        tariff.setBaseEnergyUnit("kVAh");
        tariff.setTodChargePercent(15);
        tariff.setTodStartTime("17:00");
        tariff.setTodEndTime("23:00");
        tariff.setUpdatedAt(LocalDateTime.now());
        stateTariffRepository.save(tariff);

        // 4. State Charges
        StateCharges charges = StateCharges.create();
        charges.setState(STATE_NAME);
        charges.setCategory("HV-2");
        charges.setFromDate(LocalDate.of(2024, 4, 1));
        charges.setToDate(LocalDate.of(2025, 3, 31));
        charges.setStuCharges(new BigDecimal("0.25"));
        charges.setDistributionWheelingCharges(new BigDecimal("0.45"));
        charges.setCrossSubsidy(new BigDecimal("0.38"));
        charges.setAdditionalCharge(new BigDecimal("0.10"));
        charges.setStuLossPercent(new BigDecimal("3.18"));
        charges.setWheelingLossPercent(new BigDecimal("8.00"));
        charges.setDemandFixedChargeKvaPerMonthRs(new BigDecimal("350.00"));
        charges.setUpdatedAt(LocalDateTime.now());
        stateChargesRepository.save(charges);

        // 5. ISTS Charges
        IstsCharges ists = IstsCharges.create();
        ists.setStartDate(LocalDate.of(2024, 5, 1));
        ists.setEndDate(LocalDate.of(2024, 5, 31));
        ists.setIstsLossPercent(new BigDecimal("3.42"));
        ists.setUpdatedAt(LocalDateTime.now());
        istsChargesRepository.save(ists);

        // 6. CTU Charges
        CtuCharges ctu = CtuCharges.create();
        ctu.setState(STATE_NAME);
        ctu.setMonth(202405);
        ctu.setCtuChargesRsPerKwh(new BigDecimal("0.48"));
        ctu.setUpdatedAt(LocalDateTime.now());
        ctuChargesRepository.save(ctu);

        // 7. IEX Fees
        IexFees iex = IexFees.create();
        iex.setMonth(202405);
        iex.setExchangeFees(new BigDecimal("0.02"));
        iex.setExchangeFeesGst(new BigDecimal("18.00"));
        iex.setUpdatedAt(LocalDateTime.now());
        iexFeesRepository.save(iex);

        // 8. FPPA Charges
        FppaCharges fppa = FppaCharges.create();
        fppa.setState(STATE_NAME);
        fppa.setMonth(202405);
        fppa.setFppaChargePercent(new BigDecimal("4.50"));
        fppa.setUpdatedAt(LocalDateTime.now());
        fppaChargesRepository.save(fppa);
    }

    @Test
    @DisplayName("V18B-01: Multi-market 3-way FULL OUTER JOIN returns 15-minute slot projection")
    void testCombinedExchangeRatesQuery() {
        List<ExchangeRateSlotProjection> slots = exchangeDamRateRepository
                .findCombinedExchangeRates(STATE_CODE, SIM_DATE, SIM_DATE);

        assertThat(slots).isNotNull();
        assertThat(slots).hasSize(2);

        ExchangeRateSlotProjection slot1 = slots.get(0);
        assertThat(slot1.getDate()).isEqualTo(SIM_DATE);
        assertThat(slot1.getTimeblock()).isEqualTo(1);
        assertThat(slot1.getDamMcp()).isEqualByComparingTo("4500.5000");
        assertThat(slot1.getRtmMcp()).isEqualByComparingTo("4200.2500");
        assertThat(slot1.getGdamMcp()).isEqualByComparingTo("4800.7500");

        ExchangeRateSlotProjection slot2 = slots.get(1);
        assertThat(slot2.getDate()).isEqualTo(SIM_DATE);
        assertThat(slot2.getTimeblock()).isEqualTo(2);
        assertThat(slot2.getDamMcp()).isEqualByComparingTo("5100.0000");
        assertThat(slot2.getRtmMcp()).isNull();
    }

    @Test
    @DisplayName("V18B-02: RegionState lookup resolves state code and state name interchangeably")
    void testRegionStateLookup() {
        Optional<RegionState> byCode = regionStateRepository.findFirstByStateCodeIgnoreCase(STATE_CODE);
        assertThat(byCode).isPresent();
        assertThat(byCode.get().getStateName()).isEqualTo(STATE_NAME);

        Optional<RegionState> byName = regionStateRepository.findFirstByStateNameIgnoreCase(STATE_NAME);
        assertThat(byName).isPresent();
        assertThat(byName.get().getStateCode()).isEqualTo(STATE_CODE);

        List<String> formats = dataAccessService.resolveStateFormats(STATE_CODE);
        assertThat(formats).contains(STATE_CODE, STATE_NAME);
    }

    @Test
    @DisplayName("V18B-03: StateTariffRepository retrieves matching tariffs and handles fallback")
    void testStateTariffMatchingAndFallback() {
        List<String> stateFormats = List.of(STATE_NAME, STATE_CODE);

        List<StateTariff> tariffs = dataAccessService.getStateTariffs(
                stateFormats,
                null,
                "HV-2",
                "11 kV",
                null,
                List.of(202405),
                5
        );

        assertThat(tariffs).isNotEmpty();
        StateTariff t = tariffs.get(0);
        assertThat(t.getConsumerCategory()).isEqualTo("HV-2");
        assertThat(t.getEnergyRate()).isEqualByComparingTo("6.50");
        assertThat(t.getTodChargePercent()).isEqualTo(15);
    }

    @Test
    @DisplayName("V18B-04: StateChargesRepository retrieves active losses and surcharges")
    void testStateChargesQuery() {
        List<String> stateFormats = List.of(STATE_NAME, STATE_CODE);
        Optional<StateCharges> chargesOpt = dataAccessService.getStateCharges(
                stateFormats,
                null,
                "HV-2",
                SIM_DATE
        );

        assertThat(chargesOpt).isPresent();
        StateCharges c = chargesOpt.get();
        assertThat(c.getStuLossPercent()).isEqualByComparingTo("3.18");
        assertThat(c.getWheelingLossPercent()).isEqualByComparingTo("8.00");
        assertThat(c.getCrossSubsidy()).isEqualByComparingTo("0.38");
        assertThat(c.getAdditionalCharge()).isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("V18B-05: IstsChargesRepository retrieves active charges between dates")
    void testIstsChargesQuery() {
        List<IstsCharges> ists = dataAccessService.getIstsCharges(SIM_DATE, SIM_DATE);
        assertThat(ists).isNotEmpty();
        assertThat(ists.get(0).getIstsLossPercent()).isEqualByComparingTo("3.42");
    }

    @Test
    @DisplayName("V18B-06: CTU, IEX fees, and FPPA surcharge retrieval")
    void testCtuIexAndFppaCharges() {
        List<String> stateFormats = List.of(STATE_NAME, STATE_CODE);

        Optional<CtuCharges> ctu = dataAccessService.getCtuCharges(stateFormats, 202405);
        assertThat(ctu).isPresent();
        assertThat(ctu.get().getCtuChargesRsPerKwh()).isEqualByComparingTo("0.48");

        Optional<IexFees> iex = dataAccessService.getIexFees(202405);
        assertThat(iex).isPresent();
        assertThat(iex.get().getExchangeFees()).isEqualByComparingTo("0.02");

        BigDecimal fppa = dataAccessService.getFppaPercent(stateFormats, null, 202405);
        assertThat(fppa).isEqualByComparingTo("4.50");
    }
}
