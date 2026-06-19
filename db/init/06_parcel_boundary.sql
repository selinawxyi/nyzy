-- ====================================================================
-- 为确权地块生成 GeoJSON 多边形边界 (基于中心点 + 面积估算矩形)
-- 真实场景应来自确权矢量数据导入; 此处为演示生成近似地块面
-- dx/dy 随面积变化, 使不同地块呈现不同大小
-- ====================================================================
SET NAMES utf8mb4;
USE nyzy;

UPDATE land_parcel
SET boundary = CONCAT(
  '{"type":"Polygon","coordinates":[[',
  '[', ROUND(center_lng - (0.0009 + COALESCE(area,10)*0.000018), 6), ',', ROUND(center_lat - (0.0007 + COALESCE(area,10)*0.000014), 6), '],',
  '[', ROUND(center_lng + (0.0009 + COALESCE(area,10)*0.000018), 6), ',', ROUND(center_lat - (0.0007 + COALESCE(area,10)*0.000014), 6), '],',
  '[', ROUND(center_lng + (0.0009 + COALESCE(area,10)*0.000018), 6), ',', ROUND(center_lat + (0.0007 + COALESCE(area,10)*0.000014), 6), '],',
  '[', ROUND(center_lng - (0.0009 + COALESCE(area,10)*0.000018), 6), ',', ROUND(center_lat + (0.0007 + COALESCE(area,10)*0.000014), 6), '],',
  '[', ROUND(center_lng - (0.0009 + COALESCE(area,10)*0.000018), 6), ',', ROUND(center_lat - (0.0007 + COALESCE(area,10)*0.000014), 6), ']',
  ']]}')
WHERE center_lng IS NOT NULL AND center_lat IS NOT NULL;
