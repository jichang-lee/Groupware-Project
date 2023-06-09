package org.spring.p21suck2jo.controller;

import lombok.RequiredArgsConstructor;
import org.spring.p21suck2jo.dto.ApprovingMemberNameDept;
import org.spring.p21suck2jo.dto.MemorandumDto;
import org.spring.p21suck2jo.dto.MemorandumFileDto;
import org.spring.p21suck2jo.dto.PoliceDto;
import org.spring.p21suck2jo.entity.ApprovingMember;
import org.spring.p21suck2jo.entity.MemorandumEntity;
import org.spring.p21suck2jo.entity.MemorandumFileEntity;
import org.spring.p21suck2jo.entity.PoliceEntity;
import org.spring.p21suck2jo.repository.ApprovingMemberRepository;
import org.spring.p21suck2jo.repository.MemorandumFileRepository;
import org.spring.p21suck2jo.repository.PoliceRepository;
import org.spring.p21suck2jo.service.MemorandumFileService;
import org.spring.p21suck2jo.service.MemorandumService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import javax.swing.text.html.Option;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/memo")
@RequiredArgsConstructor
public class MemorandumController {

    private final MemorandumFileService memorandumFileService;
    private final MemorandumService memorandumService;
    private final ApprovingMemberRepository approvingMemberRepository;
    private final PoliceRepository policeRepository;

    //    나의 결재함(검색어가 있고 없고의 차이로 보여준다.)
    @GetMapping("/all")
    public String memorandumPage(Model model, @PageableDefault(page = 0, size = 15, sort = "memorandumId",
            direction = Sort.Direction.DESC) Pageable pageable, @RequestParam(value = "search", required = false) String search, HttpSession currentSession) {


//            현재 Police ID를 Session에서 받아온다.
        Long policeId = Long.valueOf(String.valueOf(currentSession.getAttribute("currentPoliceId")));

        if (search == null) {
            Page<MemorandumDto> memorandumDtoPage = memorandumService.findAllMemo(policeId, pageable);

            Long total=memorandumDtoPage.getTotalElements();
            int bockNum=4;
            int nowPage= memorandumDtoPage.getNumber()+1 ;
            int startPage=Math.max(1,memorandumDtoPage.getNumber()-bockNum);
            int endPage=memorandumDtoPage.getTotalPages();

            model.addAttribute("memorandumDtoPage",memorandumDtoPage);
            model.addAttribute("total",total);
            model.addAttribute("nowPage",nowPage);
            model.addAttribute("startPage",startPage);
            model.addAttribute("endPage",endPage);
            return "memorandum/MemorandumIndex";

        } else {
            Page<MemorandumDto> memorandumDtoPage = memorandumService.memoListSearchPage(search, pageable);

            Long total=memorandumDtoPage.getTotalElements();
            int bockNum=4;
            int nowPage= memorandumDtoPage.getNumber()+1 ;
            int startPage=Math.max(1,memorandumDtoPage.getNumber()-bockNum);
            int endPage=memorandumDtoPage.getTotalPages();

            model.addAttribute("memorandumDtoPage",memorandumDtoPage);
            model.addAttribute("total",total);
            model.addAttribute("nowPage",nowPage);
            model.addAttribute("startPage",startPage);
            model.addAttribute("endPage",endPage);

            return "memorandum/MemorandumIndex";
        }


    }


    //    결재 문서 작성 페이지 이동
    @GetMapping({"/memoWritePage"})
    public String MemoWritePage(MemorandumDto memorandumDto, Model model) {
        List<PoliceDto> policeDtos = memorandumService.findDeptAndPolice();
        List<ApprovingMemberNameDept> approvingMemberNameDeptList = new ArrayList();


        for(PoliceDto i: policeDtos) {
            ApprovingMemberNameDept approvingMemberNameDept = new ApprovingMemberNameDept();
            approvingMemberNameDept.setDeptName(i.getDept().getDeptName());
            approvingMemberNameDept.setPoliceName(i.getPoliceName());
            approvingMemberNameDeptList.add(approvingMemberNameDept);
        }

        model.addAttribute("approveMember", new ApprovingMemberNameDept());
        model.addAttribute("approveMemberNameDept", approvingMemberNameDeptList);
        memorandumDto.setApproval(0);
        model.addAttribute("MemorandumDto", memorandumDto);
        return "memorandum/MemorandumWrite";
    }

    //    결재 문서 작성
    @PostMapping("/write")
    public String writeMemorandum(MemorandumDto memorandumDto, @RequestParam("file") List<MultipartFile> files, HttpSession currentSession, @RequestParam("policeName") String policeName) throws IOException {

//        Long으로 변환된 Session의 경찰 아이디는 결재문서 테이블, 결재문서 파일 테이블에 주입된다.
        Long sessionPoliceIdLong = Long.valueOf(String.valueOf(currentSession.getAttribute("currentPoliceId")));

//        결재 문서 작성
//        현재 Session에 있는 경찰 아이디를 Long 타입으로 변환해서 결재문서에 넣는다.
        Long memorandumId = memorandumService.writeMemorandum(memorandumDto, sessionPoliceIdLong);


//        파일 첨부 -> 작성한 결재 문서를 다시 찾아서, 해당 문서를 파일 테이블에 주입
        for (MultipartFile multipartFile : files) {
            memorandumFileService.putFileIntoDB(multipartFile, memorandumDto, memorandumId, sessionPoliceIdLong);
        }

        memorandumService.setApprovingMember(policeName, memorandumId);
        return "redirect:/memo/all";
    }


    //    결재 문서 수정페이지 이동
//    수정할 결재 문서 내의 모든 데이터를 이동
    @GetMapping("/updateMemoPage/{memorandumId}")
    public String updateMemoPage(@PathVariable Long memorandumId, Model model) {

//        수정할 문서의 내용 전체 전달
        MemorandumEntity memorandumEntity = memorandumService.findSelectedMemo(memorandumId);
        MemorandumDto memorandumDto = MemorandumDto.builder()
                .memorandumTitle(memorandumEntity.getMemorandumTitle())
                .memorandumContent(memorandumEntity.getMemorandumContent())
                .build();
        model.addAttribute("MemorandumDto", memorandumDto);

//        수정할 문서에 들어있는 첨부 파일 전달
        List<MemorandumFileEntity> memorandumFileEntityList = memorandumFileService.findAllFilesInSelectedMemo(memorandumId);
        List<MemorandumFileDto> memorandumFileDtoList = new ArrayList<>();
        for (MemorandumFileEntity memorandumFileEntity : memorandumFileEntityList) {
            MemorandumFileDto memorandumFileDto = MemorandumFileDto.builder()
                    .memorandumFileId(memorandumFileEntity.getMemorandumFileId())
                    .memorandumFileOriginalName(memorandumFileEntity.getMemorandumFileOriginalName())
                    .build();
            memorandumFileDtoList.add(memorandumFileDto);
        }

        model.addAttribute("fileInDetailMemo", memorandumFileDtoList);
        model.addAttribute("memorandumId", memorandumId);

//        결재자 수정을 위해 모든 경찰의 정보를 전달
        List<PoliceDto> policeDtos = memorandumService.findDeptAndPolice();
        List<ApprovingMemberNameDept> approvingMemberNameDeptList = new ArrayList();


        for(PoliceDto i: policeDtos) {
            ApprovingMemberNameDept approvingMemberNameDept = new ApprovingMemberNameDept();
            approvingMemberNameDept.setDeptName(i.getDept().getDeptName());
            approvingMemberNameDept.setPoliceName(i.getPoliceName());
            approvingMemberNameDeptList.add(approvingMemberNameDept);
        }

        model.addAttribute("approveMember", new ApprovingMemberNameDept());
        model.addAttribute("approveMemberNameDept", approvingMemberNameDeptList);


        return "memorandum/MemorandumUpdate";
    }

    //    결재 문서 수정하기
    @PostMapping("/updateMemo/{memorandumId}")
    public String updateMemo(@PathVariable Long memorandumId, @RequestParam("file") List<MultipartFile> files, HttpSession currentSession, MemorandumDto memorandumDto, @RequestParam("policeName") String approvingPoliceName) throws IOException {


        //Long memorandumIdLong = Long.valueOf(String.valueOf(memorandumId));

//        수정할 결재 문서 상세 내용 보여주기
        MemorandumEntity memorandumEntity = memorandumService.findSelectedMemo(memorandumId);
//        수정할 결재 문서에 제목, 내용 등을 View에서 받은 값으로 저장
        memorandumEntity.setMemorandumTitle(memorandumDto.getMemorandumTitle());
        memorandumEntity.setMemorandumContent(memorandumDto.getMemorandumContent());

//        수정할 결재 문서에 해당하는 Approving Member찾기
        MemorandumEntity memoInApprovingMember = new MemorandumEntity();
        memoInApprovingMember.setMemorandumId(memorandumId);
        ApprovingMember updatedApprovingMember = approvingMemberRepository.findByMemorandum(memoInApprovingMember).get();
        Optional<PoliceEntity> updatedApprovingPolice = policeRepository.findByPoliceName(approvingPoliceName);
        updatedApprovingMember.setPolice(updatedApprovingPolice.get());

        approvingMemberRepository.save(updatedApprovingMember);


//      session의 getAttribute Object를 Long으로 변환
//        Long으로 변환된 Session의 경찰 아이디는 결재문서 테이블, 결재문서 파일 테이블에 주입된다.
        Long sessionPoliceIdLong = Long.valueOf(String.valueOf(currentSession.getAttribute("currentPoliceId")));

//        수정하기
        memorandumService.updateMemorandum(memorandumEntity, sessionPoliceIdLong);

//        결재 문서에 해당 하는 파일 보여주기
        List<MemorandumFileEntity> memorandumFileEntityList = memorandumFileService.findAllFilesInSelectedMemo(memorandumId);
        List<MemorandumFileDto> memorandumFileDtoList = new ArrayList<>();
        for (MemorandumFileEntity memorandumFileEntity : memorandumFileEntityList) {
            MemorandumFileDto memorandumFileDto = MemorandumFileDto.builder()
                    .memorandumFileId(memorandumFileEntity.getMemorandumFileId())
                    .memorandumFileOriginalName(memorandumFileEntity.getMemorandumFileOriginalName())
                    .build();
            memorandumFileDtoList.add(memorandumFileDto);
        }
//        파일 첨부 -> 작성한 결재 문서를 다시 찾아서, 해당 문서를 파일 테이블에 주입
        for (MultipartFile multipartFile : files) {
            memorandumFileService.putFileIntoDB(multipartFile, memorandumDto, memorandumId, sessionPoliceIdLong);
        }

        return "redirect:/memo/all";
    }


    //    결재 문서 상세 보기
    @GetMapping("/memoDetail/{memorandumId}")
    public String detailMemoPage(@PathVariable String memorandumId, Model model) {

        Long memorandumIdLong = Long.valueOf(String.valueOf(memorandumId));

//        결재 문서 상세 내용 보여주기
        MemorandumEntity memorandumEntity = memorandumService.findSelectedMemo(memorandumIdLong);
        MemorandumDto memorandumDto = MemorandumDto.builder()
                .memorandumId(memorandumEntity.getMemorandumId())
                .memorandumTitle(memorandumEntity.getMemorandumTitle())
                .memorandumContent(memorandumEntity.getMemorandumContent())
                .approval(memorandumEntity.getApproval())
                .createTime(memorandumEntity.getCreateTime())
                .build();

        model.addAttribute("detailMemo", memorandumDto);

//        결재 문서에 해당 하는 파일 보여주기
        List<MemorandumFileEntity> memorandumFileEntityList = memorandumFileService.findAllFilesInSelectedMemo(memorandumIdLong);
        List<MemorandumFileDto> memorandumFileDtoList = new ArrayList<>();
        for (MemorandumFileEntity memorandumFileEntity : memorandumFileEntityList) {
            MemorandumFileDto memorandumFileDto = MemorandumFileDto.builder()
                    .memorandumFileId(memorandumFileEntity.getMemorandumFileId())
                    .memorandumFileOriginalName(memorandumFileEntity.getMemorandumFileOriginalName())
                    .build();
            memorandumFileDtoList.add(memorandumFileDto);
        }

        model.addAttribute("fileInDetailMemo", memorandumFileDtoList);
        String policeName = memorandumService.findApprovingMember(memorandumIdLong);
        model.addAttribute("policeName", policeName);

        return "memorandum/MemorandumDetail";


    }


    //    결재 문서 삭제하기
    @GetMapping("/delete/{memorandumId}")
    public String deleteSelectedMemo(@PathVariable("memorandumId") Long memoId, HttpSession currentSession) {

//        결재 문서 찾아서 삭제
        memorandumService.deleteSelectedMemo(memoId);

//        결재 문서에 해당 하는 파일 삭제
        memorandumFileService.deleteFilesInSelectedMemo(memoId);


        //      session의 getAttribute Object를 Long으로 변환
//        Long으로 변환된 Session의 경찰 아이디는 결재문서 테이블, 결재문서 파일 테이블에 주입된다.
        Long sessionPoliceIdLong = Long.valueOf(String.valueOf(currentSession.getAttribute("currentPoliceId")));

        return "redirect:/memo/all";
    }


}
