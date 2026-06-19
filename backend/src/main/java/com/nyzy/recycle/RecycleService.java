package com.nyzy.recycle;

import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.recycle.mapper.RecycleMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

@Service
public class RecycleService {

    /** 回收站保留天数 */
    public static final int RETENTION_DAYS = 90;

    /** 业务类型 -> {表名, 中文名}. 白名单, 防止 SQL 注入 */
    private static final Map<String, String[]> TYPES = new LinkedHashMap<>();
    static {
        TYPES.put("parcel",   new String[]{"land_parcel", "确权地块"});
        TYPES.put("abandon",  new String[]{"abandon_parcel", "撂荒地块"});
        TYPES.put("planting", new String[]{"planting_record", "种植记录"});
        TYPES.put("quality",  new String[]{"land_quality", "耕地质量"});
        TYPES.put("water",    new String[]{"water_facility", "水利设施"});
        TYPES.put("support",  new String[]{"support_facility", "配套设施"});
    }

    private final RecycleMapper mapper;

    public RecycleService(RecycleMapper mapper) {
        this.mapper = mapper;
    }

    /** 聚合所有模块的回收站记录, 可按业务类型过滤 */
    public List<RecycleItem> list(String bizType) {
        Map<String, Supplier<List<RecycleItem>>> loaders = new LinkedHashMap<>();
        loaders.put("parcel", mapper::listParcel);
        loaders.put("abandon", mapper::listAbandon);
        loaders.put("planting", mapper::listPlanting);
        loaders.put("quality", mapper::listQuality);
        loaders.put("water", mapper::listWater);
        loaders.put("support", mapper::listSupport);

        List<RecycleItem> all = new ArrayList<>();
        loaders.forEach((type, loader) -> {
            if (bizType != null && !bizType.isEmpty() && !bizType.equals(type)) return;
            all.addAll(loader.get());
        });
        for (RecycleItem item : all) {
            item.setBizTypeName(TYPES.get(item.getBizType())[1]);
            item.setDaysLeft(daysLeft(item.getDeletedAt()));
        }
        all.sort(Comparator.comparing(RecycleItem::getDeletedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return all;
    }

    /** 恢复 */
    public void restore(String bizType, Long id) {
        String table = table(bizType);
        if (mapper.restore(table, id) == 0) throw new ApiException("恢复失败, 记录不存在");
    }

    /** 彻底删除 (物理): 仅管理员 */
    public void purge(String bizType, Long id) {
        if (!UserContext.isAdmin()) throw new ApiException(403, "仅管理员可彻底删除");
        String table = table(bizType);
        if (mapper.purge(table, id) == 0) throw new ApiException("删除失败, 记录不存在");
    }

    /** 每日 03:00 物理删除超过保留期的回收站记录 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void autoPurgeExpired() {
        LocalDateTime before = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int total = 0;
        for (String[] cfg : TYPES.values()) {
            total += mapper.purgeExpired(cfg[0], before);
        }
        if (total > 0) {
            System.out.println("[RecycleService] 自动清理过期回收站记录 " + total + " 条");
        }
    }

    private String table(String bizType) {
        String[] cfg = TYPES.get(bizType);
        if (cfg == null) throw new ApiException("非法的业务类型: " + bizType);
        return cfg[0];
    }

    private Integer daysLeft(LocalDateTime deletedAt) {
        if (deletedAt == null) return RETENTION_DAYS;
        long passed = Duration.between(deletedAt, LocalDateTime.now()).toDays();
        return (int) Math.max(0, RETENTION_DAYS - passed);
    }
}
