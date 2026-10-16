package ru.utility.accounting.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.utility.accounting.dto.UtilityChargeDto;
import ru.utility.accounting.entity.Meter;
import ru.utility.accounting.entity.Premises;
import ru.utility.accounting.entity.UtilityCharge;
import ru.utility.accounting.repository.MeterRepository;
import ru.utility.accounting.repository.PremisesRepository;
import ru.utility.accounting.repository.UtilityChargeRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class UtilityChargeService {

    private final UtilityChargeRepository utilityChargeRepository;
    private final PremisesRepository premisesRepository;
    private final MeterRepository meterRepository;

    public UtilityChargeService(UtilityChargeRepository utilityChargeRepository,
                                PremisesRepository premisesRepository,
                                MeterRepository meterRepository) {
        this.utilityChargeRepository = utilityChargeRepository;
        this.premisesRepository = premisesRepository;
        this.meterRepository = meterRepository;
    }

    public List<UtilityCharge> findAll() {
        return utilityChargeRepository.findAll();
    }

    public UtilityCharge findById(Long id) {
        return utilityChargeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Начисление не найдено с id: " + id));
    }

    @Transactional
    public UtilityCharge create(UtilityCharge charge, Long premisesId, Long meterId) {
        charge.setCreatedAt(OffsetDateTime.now());

        if (premisesId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID помещения обязателен");
        }
        Premises premises = premisesRepository.findById(premisesId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Помещение не найдено с id: " + premisesId));
        charge.setPremises(premises);

        Meter meter = null;
        if (meterId != null) {
            meter = meterRepository.findById(meterId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Счётчик не найден с id: " + meterId));

            if (!meter.getPremises().getId().equals(premises.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Счётчик не принадлежит указанному помещению");
            }
            charge.setMeter(meter);
        }

        if (charge.getPeriod() == null || charge.getPeriod().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Период начисления обязателен");
        }
        if (charge.getVolume() == null || charge.getVolume() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Объём не может быть отрицательным");
        }

        if (charge.getAmount() == null && meter != null && meter.getUnitRate() != null) {
            double calculatedAmount = charge.getVolume() * meter.getUnitRate();
            charge.setAmount(Math.round(calculatedAmount * 100.0) / 100.0);
        } else if (charge.getAmount() == null) {
            charge.setAmount(0.0);
        }

        if (charge.getStatus() == null || charge.getStatus().isBlank()) {
            charge.setStatus("CALCULATED");
        }

        return utilityChargeRepository.save(charge);
    }

    @Transactional
    public UtilityCharge patch(Long id, UtilityChargeDto dto) {
        UtilityCharge charge = findById(id);

        if (dto.getPeriod() != null && !dto.getPeriod().isBlank()) {
            charge.setPeriod(dto.getPeriod());
        }
        if (dto.getVolume() != null && dto.getVolume() >= 0) {
            charge.setVolume(dto.getVolume());
            if (dto.getAmount() == null && charge.getMeter() != null) {
                double recalculated = charge.getVolume() * charge.getMeter().getUnitRate();
                charge.setAmount(Math.round(recalculated * 100.0) / 100.0);
            }
        }
        if (dto.getAmount() != null && dto.getAmount() >= 0) {
            charge.setAmount(dto.getAmount());
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            charge.setStatus(dto.getStatus());
        }
        if (dto.getPremisesId() != null) {
            Premises premises = premisesRepository.findById(dto.getPremisesId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Помещение не найдено с id: " + dto.getPremisesId()));
            charge.setPremises(premises);
        }
        if (dto.getMeterId() != null) {
            Meter meter = meterRepository.findById(dto.getMeterId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Счётчик не найден с id: " + dto.getMeterId()));
            charge.setMeter(meter);
        }

        return utilityChargeRepository.save(charge);
    }

    @Transactional
    public void delete(Long id) {
        UtilityCharge charge = findById(id);
        utilityChargeRepository.delete(charge);
    }
}

