package com.nyzy.recycle.mapper;

import com.nyzy.recycle.RecycleItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收站专用 Mapper. 直接写 SQL 绕过 @TableLogic 逻辑删除拦截,
 * 以查询/恢复/物理删除 is_deleted=1 的记录.
 * restore/purge 的表名由服务层白名单校验后传入.
 * (置于 *.mapper 包下, 由 @MapperScan("com.nyzy.**.mapper") 注册)
 */
public interface RecycleMapper {

    @Select("SELECT 'parcel' AS biz_type, id AS biz_id, name AS title, parcel_code AS subtitle, " +
            "delete_reason, deleted_by, deleted_at FROM land_parcel WHERE is_deleted=1")
    List<RecycleItem> listParcel();

    @Select("SELECT 'abandon' AS biz_type, id AS biz_id, parcel_name AS title, " +
            "CONCAT(parcel_code, ' / ', IFNULL(abandon_year,'')) AS subtitle, " +
            "delete_reason, deleted_by, deleted_at FROM abandon_parcel WHERE is_deleted=1")
    List<RecycleItem> listAbandon();

    @Select("SELECT 'planting' AS biz_type, id AS biz_id, parcel_name AS title, " +
            "CONCAT(IFNULL(plant_year,''), '年 ', IFNULL(crop,'')) AS subtitle, " +
            "delete_reason, deleted_by, deleted_at FROM planting_record WHERE is_deleted=1")
    List<RecycleItem> listPlanting();

    @Select("SELECT 'quality' AS biz_type, id AS biz_id, parcel_name AS title, " +
            "CONCAT(IFNULL(eval_year,''), '年 地力', IFNULL(grade,''), '等') AS subtitle, " +
            "delete_reason, deleted_by, deleted_at FROM land_quality WHERE is_deleted=1")
    List<RecycleItem> listQuality();

    @Select("SELECT 'water' AS biz_type, id AS biz_id, name AS title, type AS subtitle, " +
            "delete_reason, deleted_by, deleted_at FROM water_facility WHERE is_deleted=1")
    List<RecycleItem> listWater();

    @Select("SELECT 'support' AS biz_type, id AS biz_id, name AS title, NULL AS subtitle, " +
            "delete_reason, deleted_by, deleted_at FROM support_facility WHERE is_deleted=1")
    List<RecycleItem> listSupport();

    /** 恢复: 表名已白名单校验 */
    @Update("UPDATE ${table} SET is_deleted=0, delete_reason=NULL, deleted_by=NULL, deleted_at=NULL WHERE id=#{id}")
    int restore(@Param("table") String table, @Param("id") Long id);

    /** 物理删除: 表名已白名单校验 */
    @Delete("DELETE FROM ${table} WHERE id=#{id} AND is_deleted=1")
    int purge(@Param("table") String table, @Param("id") Long id);

    /** 物理删除过期(超90天)的回收站记录 */
    @Delete("DELETE FROM ${table} WHERE is_deleted=1 AND deleted_at IS NOT NULL AND deleted_at < #{before}")
    int purgeExpired(@Param("table") String table, @Param("before") LocalDateTime before);
}
