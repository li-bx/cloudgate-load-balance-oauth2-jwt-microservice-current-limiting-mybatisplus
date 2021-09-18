package com.hd.microsysservice.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hd.common.vo.SyMenuBtnVo;
import com.hd.common.vo.SyMenuVo;
import com.hd.common.vo.SyUserVo;
import com.hd.microsysservice.entity.*;
import com.hd.microsysservice.mapper.SyEnterpriseMapper;
import com.hd.microsysservice.mapper.SyMaintainMapper;
import com.hd.microsysservice.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wli
 * @since 2021-07-13
 */
@Service
public class SyEnterpriseServiceImpl extends ServiceImpl<SyEnterpriseMapper, SyEnterpriseEntity> implements SyEnterpriseService {

    @Autowired
    SyMenuService syMenuService;
    @Autowired
    SyMenuBtnService syMenuBtnService;
    @Autowired
    SyRoleService syRoleService;
    @Autowired
    SyRolePermService syRolePermService;
    @Autowired
    SyOrgService syOrgService;
    @Autowired
    SyUserRoleService syUserRoleService;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class}, isolation = Isolation.DEFAULT)
    public void createEnterprise(SyEnterpriseEntity syEnterpriseEntity, Boolean createRoles) throws Exception {
        //TODO: 创建企业，并复制root企业的菜单到当前企业
        save(syEnterpriseEntity);
        //复制菜单
        HashMap<Long, Long> menuBtnIdMap = new HashMap<>();
        List<SyMenuVo> rootMenuVos = syMenuService.getAllMenu("root");
        rootMenuVos.forEach((menuvo) -> {
            if (menuvo.getName().compareTo("服务监视") != 0 && menuvo.getName().compareTo("配置管理") != 0) {
                copyMenu(menuvo, syEnterpriseEntity.getEnterpriseId(), null, menuBtnIdMap);
            }
        });
        //复制角色
        if (createRoles) {
            QueryWrapper queryWrapper = new QueryWrapper() {{
                eq("enterprise_id", "root");
            }};
            List<SyRoleEntity> rootRoles = syRoleService.list(queryWrapper);
            rootRoles.forEach(r -> {
                //if(r.getId()!=8888888888888888888L){
                if (r.getName().compareTo("超级root") != 0) {
                    copyRole(r, syEnterpriseEntity.getEnterpriseId(), menuBtnIdMap);
                }
            });
        }
        //初始化部门
        SyOrgEntity syOrgEntity = new SyOrgEntity() {{
            setName(syEnterpriseEntity.getName());
            setEnterpriseId(syEnterpriseEntity.getEnterpriseId());
            setLevelCode("100");
            setType((short) 0);
        }};
        syOrgService.save(syOrgEntity);
        //初始化管理员
        SyUserVo syUserVo = new SyUserVo() {{
            setName("管理员");
            setAccount("admin");
            setEnterpriseId(syEnterpriseEntity.getEnterpriseId());
            setPasswordMd5("1234");
            setOrgId(syOrgEntity.getId());
        }};
        syUserService.createUser(syUserVo);
        //初始化管理员角色
        QueryWrapper queryWrapper = new QueryWrapper() {{
            eq("enterprise_id", syEnterpriseEntity.getEnterpriseId());
            eq("account", "admin");
        }};
        SyUserEntity syUserEntity = syUserService.getOne(queryWrapper);
        queryWrapper = new QueryWrapper() {{
            eq("enterprise_id", syEnterpriseEntity.getEnterpriseId());
            eq("name", "管理员");
        }};
        SyRoleEntity syRoleEntity = syRoleService.getOne(queryWrapper);
        SyUserRoleEntity syUserRoleEntity=new SyUserRoleEntity(){{
            setRoleId(syRoleEntity.getId());
            setUserId(syUserEntity.getId());
        }};
        syUserRoleService.save(syUserRoleEntity);
    }

    @Autowired
    SyEnterpriseService syEnterpriseService;

    @Autowired
    SyUserService syUserService;

    @Autowired
    SyMaintainMapper syMaintainMapper;

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class}, isolation = Isolation.DEFAULT)
    @Override
    public void removeEnterpriseById(Long id) throws Exception {
        SyEnterpriseEntity syEnterpriseEntity = syEnterpriseService.getById(id);
        Assert.isTrue(syEnterpriseEntity != null, String.format("企业Id:%s不存在!", id));
        Assert.isTrue(syEnterpriseEntity.getEnterpriseId().compareTo("root") != 0, String.format("不能删除特殊企业%s!", "root"));
        syEnterpriseService.removeById(id);
        //移除本地用户
        QueryWrapper qw = new QueryWrapper();
        qw.eq("enterprise_id", syEnterpriseEntity.getEnterpriseId());
        syUserService.remove(qw);
        //移除用户中心的用户
        syUserService.removeAllUserForCenter(syEnterpriseEntity.getEnterpriseId());
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class}, isolation = Isolation.DEFAULT)
    @Override
    public void deleteEnterprisePhysically(Long id) {
        String enterpeiseId = getById(id).getEnterpriseId();
        syMaintainMapper.deleteEnterprisePhysically(enterpeiseId);
        //removeById(id);
    }

    private void copyRole(SyRoleEntity r, String enterpriseId, HashMap<Long, Long> menuBtnIdMap) {
        Long oldRoleId = r.getId();
        r.setEnterpriseId(enterpriseId);
        r.setId(null);
        syRoleService.save(r);
        //保存权限
        QueryWrapper queryWrapper = new QueryWrapper() {{
            eq("role_id", oldRoleId);
        }};
        List<SyRolePermEntity> oldRolePerms = syRolePermService.list(queryWrapper);
        oldRolePerms.forEach(item -> {
            item.setRoleId(r.getId());
            item.setId(null);
            item.setMenuBtnId(menuBtnIdMap.get(item.getMenuBtnId()));
            //syRolePermService.save(item);
        });
        syRolePermService.saveBatch(oldRolePerms);
    }

    SyMenuService.SyMenuVoConvertUtils syMenuVoConvertUtils = new SyMenuService.SyMenuVoConvertUtils();

    /**
     * 递归复制menu
     *
     * @param menuvo
     */
    private void copyMenu(SyMenuVo menuvo, String enterId, Long parentMenuId, HashMap<Long, Long> menuBtnIdMap) {
        menuvo.setEnterpriseId(enterId);
        menuvo.setParentId(parentMenuId);
        menuvo.setId(null);
        SyMenuEntity syMenuEntity = syMenuVoConvertUtils.convertToT1(menuvo);
        syMenuService.save(syMenuEntity);
        if (menuvo.getType() == 0) {
            //目录,创建子目录
            List<SyMenuVo> childs = menuvo.getChilds();
            if (childs != null) {
                childs.forEach(m -> {
                    copyMenu(m, enterId, syMenuEntity.getId(), menuBtnIdMap);
                });
            }

        } else {
            //菜单，创建菜单btns
            List<SyMenuBtnVo> btns = menuvo.getBtns();
            if (btns != null) {
                btns.forEach(btn -> {
                    btn.setEnterpriseId(enterId);
                    btn.setMenuId(syMenuEntity.getId());
                    Long oldMenuBtnId = btn.getId();
                    btn.setId(null);
                    SyMenuBtnEntity syMenuBtnEntity = syMenuBtnVoConvertUtils.convertToT1(btn);
                    syMenuBtnService.save(syMenuBtnEntity);
                    menuBtnIdMap.put(oldMenuBtnId, syMenuBtnEntity.getId());
                });
            }
        }
    }

    SyMenuBtnService.SyMenuBtnVoConvertUtils syMenuBtnVoConvertUtils = new SyMenuBtnService.SyMenuBtnVoConvertUtils();

}
