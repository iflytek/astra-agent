import http from '@/utils/http';
import { message } from 'antd';
import {
  ListToolSquareParams,
  EnableToolFavoriteParams,
  GetToolDetailParams,
} from '@/types/plugin-store';
import type { ResponseResultPage } from '@/types/global';
import type { Tool, ToolDetail } from '@/types/plugin-store';
/**
 * 获取插件列表
 * @param params 请求参数
 * @returns 插件列表
 */
export function listToolSquare(
  params: ListToolSquareParams
): Promise<ResponseResultPage<Tool>> {
  return http.post('/tool/list-tool-square', params);
}

/**
 * 收藏插件
 * @param params 请求参数
 * @returns 收藏结果
 */
export async function enableToolFavorite(
  params: EnableToolFavoriteParams
): Promise<number> {
  return await http.get('/tool/favorite', { params });
}

/**
 * 获取插件详情
 * @param params 请求参数
 * @returns 插件详情
 */
export async function getToolDetail(
  params: GetToolDetailParams
): Promise<ToolDetail> {
  try {
    const response = await http.get('/tool/detail', {
      params,
    });
    if (response?.data.code !== 0) {
      throw new Error(response.data.message);
    }
    return response.data.data;
  } catch (error) {
    message.error(error instanceof Error ? error.message : '获取插件详情失败');
    throw error;
  }
}
