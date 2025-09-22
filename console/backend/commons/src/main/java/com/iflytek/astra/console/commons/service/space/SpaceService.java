package com.iflytek.astra.console.commons.service.space;


import com.iflytek.astra.console.commons.dto.space.EnterpriseSpaceCountVO;
import com.iflytek.astra.console.commons.dto.space.SpaceVO;
import com.iflytek.astra.console.commons.entity.space.Space;
import com.iflytek.astra.console.commons.enums.space.SpaceTypeEnum;

import java.util.List;

/**
 * Space
 */
public interface SpaceService {

    List<SpaceVO> recentVisitList();

    List<SpaceVO> personalList(String name);

    List<SpaceVO> personalSelfList(String name);

    List<SpaceVO> corporateJoinList(String name);

    List<SpaceVO> corporateList(String name);

    EnterpriseSpaceCountVO corporateCount();

    SpaceVO getSpaceVO();

    void setLastVisitPersonalSpaceTime();

    SpaceVO getLastVisitSpace();

    Long countByEnterpriseId(Long enterpriseId);

    Long countByUid(String uid);

    Space getSpaceById(Long id);

    List<SpaceVO> listByEnterpriseIdAndUid(Long enterpriseId, String uid);

    boolean checkExistByName(String name, Long id);

    SpaceTypeEnum getSpaceType(Long spaceId);

    boolean save(Space space);

    Space getById(Long id);

    boolean removeById(Long id);

    boolean updateById(Space space);

}
