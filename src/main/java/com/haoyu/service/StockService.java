package com.haoyu.service;

import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.springframework.stereotype.Service;

import com.haoyu.beans.PageQuery;
import com.haoyu.beans.PageResult;
import com.haoyu.dao.MesOrderMapper;
import com.haoyu.dao.MesProductMapper;
import com.haoyu.dao.MesStockCustomerMapper;
import com.haoyu.dao.MesStockMapper;
import com.haoyu.dao.MesStorageCustomerMapper;
import com.haoyu.dto.SearchStockDto;
import com.haoyu.dto.StockDto;
import com.haoyu.model.MesOrder;
import com.haoyu.model.MesProduct;
import com.haoyu.model.MesStock;
import com.haoyu.param.MesStockVo;
import com.haoyu.param.SearchStockParam;
import com.haoyu.util.BeanValidator;

@Service
public class StockService {
	@Resource
	private MesStockMapper stockMapper;
	@Resource
	private MesProductMapper productMapper;
	@Resource
	private MesOrderMapper orderMapper;
	@Resource
	private MesStorageCustomerMapper mesStorageCustomerMapper;
	@Resource
	private MesStockCustomerMapper mesStockCustomerMapper;
	@Resource
	private SqlSession sqlSession;
	//增加操作
	public void insert(MesStockVo stockVo) {
		//TODO  vo  上必须要写校验注解
		BeanValidator.check(stockVo);
		
		//增加操作
		MesStock mesStock=MesStock.builder().stockProductid(stockVo.getStockProductid()).stockProductname(stockVo.getStockProductname())//
				.stockImgid(stockVo.getStockImgid()).stockProductsource(stockVo.getStockProductsource())//
				.stockStatus(stockVo.getStockStatus()).stockRemark(stockVo.getStockRemark())//
				.stockStorageid(stockVo.getStockStorageid()).build();
		
		//Order*
		MesProduct product=productMapper.selectByPrimaryKey(mesStock.getStockProductid());
		if(product!=null) {
			Integer orderid=product.getProductOrderid();
			MesOrder mesOrder=orderMapper.selectByPrimaryKey(orderid);
			if(mesOrder!=null) {
				mesStock.setStockOrderid(orderid);
				mesStock.setStockOrdername(mesOrder.getOrderProductname());
			}
			//mesStock.setStockProductname(product.getProductMaterialname());
			mesStock.setStockStoragestatus(1);//待入库
		}
		
		stockMapper.insertSelective(mesStock);
	}
	//库存信息分页
	public PageResult<StockDto> searchPageList(SearchStockParam param, PageQuery page) {
		// 校验
		BeanValidator.check(page);
		// vo-dto
		SearchStockDto dto = new SearchStockDto();

		if (StringUtils.isNotBlank(param.getKeyword())) {
			dto.setKeyword("%" + param.getKeyword() + "%");
		}
		//TODO fromtime  totime
		if (StringUtils.isNotBlank(param.getSearch_status())) {
			dto.setSearch_status(Integer.parseInt(param.getSearch_status()));
		}
		
		if (StringUtils.isNotBlank(param.getStoragename())) {
			dto.setStoragename(Integer.parseInt(param.getStoragename()));
		}
		
		if(StringUtils.isNotBlank(param.getStock_status())) {
			dto.setStock_status(Integer.parseInt(param.getStock_status()));
		}

		int count = mesStockCustomerMapper.countBySearchDto(dto);
		if (count > 0) {
			List<StockDto> stockList = mesStockCustomerMapper.getPageListBySearchDto(dto, page);
			return PageResult.<StockDto>builder().total(count).data(stockList).build();
		}

		return PageResult.<StockDto>builder().build();
	}
	//批量质检入库
	public void batchCheck(String ids, String stockCheck) {
		if (StringUtils.isNotBlank(ids) && StringUtils.isNotBlank(stockCheck)) {
			MesStockMapper stockMapper=sqlSession.getMapper(MesStockMapper.class);
			String[] idCollection=ids.split("&");
			for(String idTemp:idCollection) {
				Integer id=Integer.parseInt(idTemp);
				MesStock stock=stockMapper.selectByPrimaryKey(id);
				//待入库库存信息才可以成为已入库状态
				if(stock.getStockStoragestatus().equals(1)) {
					stock.setStockCheckremark(stockCheck);
					stock.setStockStoragestatus(2);
					stock.setStockIntime(new Date());
					stockMapper.updateByPrimaryKeySelective(stock);
				}
			}
		}
	}
	//批量出库
	public void batchOut(String ids, String stockOutObj, String stockOutRemark) {
		//非空判断
		if (StringUtils.isNotBlank(ids) && StringUtils.isNotBlank(stockOutObj)) {
			MesStockMapper stockMapper=sqlSession.getMapper(MesStockMapper.class);
			//1&2&3  1  2  3
			String[] idCollection=ids.split("&");
			for(String idTemp:idCollection) {
				Integer id=Integer.parseInt(idTemp);
				MesStock stock=stockMapper.selectByPrimaryKey(id);
				//已出库
				//只能对已入库对象进行出库操作
				if(stock.getStockStoragestatus().equals(2)) {
					stock.setStockStoragestatus(3);
					stock.setStockOutobj(stockOutObj);
					stock.setStockRemark(stockOutRemark);
					stock.setStockOuttime(new Date());
					stockMapper.updateByPrimaryKeySelective(stock);
					//出库后续流程
					stockOutProcess(id,stockMapper);
				}
			}
		}
		
	}
	//出库后续流程
	private void stockOutProcess(Integer id,MesStockMapper stockMapper) {
		//出库对象判断
		MesStock stock=stockMapper.selectByPrimaryKey(id);
		if(stock.getStockStoragestatus().equals(3)) {
			String outobj=stock.getStockOutobj();
			if(StringUtils.isNotBlank(outobj)) {
				//TODO
				//入库操作
				stock.setId(null);
				stock.setStockStoragestatus(1);
				stock.setStockCheckremark(null);
				stock.setStockRemark(null);
				stock.setStockIntime(null);
				stock.setStockOuttime(null);
				String stockName=stock.getStockOutobj();
				stock.setStockOutobj(null);
				Integer storageId=mesStorageCustomerMapper.selectIdByName(stockName);
				stock.setStockStorageid(storageId);
				//TODO  user ip  time
				
				stockMapper.insertSelective(stock);
//				if(outobj.equals("原料库")||outobj.equals("半成品库")||outobj.equals("成品库")||outobj.equals("废料库")) {
//					//1,库房
//					
//				}else if(outobj.equals("锻造车间")||outobj.equals("热处理车间")||outobj.equals("机加车间")) {
//					//2,车间
//					
//				}else {
//					//3,用户
//					
//				}
			}
		}
	}
	
	
	
	
	
}