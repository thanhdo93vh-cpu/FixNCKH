package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.dto.TrashReasoningResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TrashReasoningRepository {

    private final Neo4jClient neo4jClient;

    public Optional<TrashReasoningResultDTO> reasonByLabel(String label) {

        String cypher = """
    MATCH (i:TrashItem)

    WHERE toLower(i.label) = toLower($label)
       OR toLower(i.labelDisplay) = toLower($label)
       OR EXISTS {
           MATCH (i)-[:HAS_ALIAS]->(a:TrashItemAlias)
           WHERE toLower(a.alias) = toLower($label)
              OR toLower(a.aliasLabel) = toLower($label)
              OR toLower(a.aliasDisplay) = toLower($label)
       }

    OPTIONAL MATCH (i)-[:MADE_OF]->(v:VatLieu)

    OPTIONAL MATCH (i)-[:APPLIES_RULE]->(directRule:Rule)

    OPTIONAL MATCH (v)-[:APPLIES_RULE]->(materialRule:Rule)
    WITH
        i,
        collect(DISTINCT v.code) AS materials,
        collect(DISTINCT v.description) AS materialDescriptions,
        collect(DISTINCT directRule) AS directRules,
        collect(DISTINCT materialRule) AS materialRules
    
    WITH
         i,
         materials,
         materialDescriptions,
         directRules + materialRules AS allRules
   
    UNWIND allRules AS candidateRule
    
    WITH
        i,
        materials,
        materialDescriptions,
        candidateRule
    
    WHERE candidateRule IS NOT NULL
    
    ORDER BY coalesce(candidateRule.priority, 0) DESC
    
    WITH
        i,
        materials,
        materialDescriptions,
        collect(candidateRule)[0] AS rule
    
    WHERE rule IS NOT NULL

    MATCH (rule)-[:INFERS_TYPE]->(t:TrashType)
    MATCH (t)-[:USES_BIN]->(b:TrashBin)

    OPTIONAL MATCH (rule)-[:REQUIRES_TREATMENT]->(tr:Treatment)

    // Guidance lấy trực tiếp từ Rule
    OPTIONAL MATCH (rule)-[:HAS_GUIDANCE]->(g:HuongXuLy)

    OPTIONAL MATCH (i)-[:HAS_HAZARD]->(h:Hazard)
    
    OPTIONAL MATCH (i)-[:HAS_IMPACT]->(imp:Impact)
    OPTIONAL MATCH (rule)-[:SUPPORTED_BY]->(s:Source)

    WITH i,
         materials,
         materialDescriptions,
         rule,
         t,
         b,
         tr,
         collect(DISTINCT g.ten) AS guidance,
         collect(DISTINCT g.moTa) AS guidanceDescription,
         collect(DISTINCT g.luuY) AS guidanceLuY,
         reduce(steps = [], groupSteps IN collect(DISTINCT g.cacBuoc) | steps + groupSteps) AS guidanceSteps,
         collect(DISTINCT coalesce(h.name, h.code)) AS hazards,
         collect(DISTINCT h.description) AS hazardDescriptions,
         collect(DISTINCT CASE WHEN h.code IN ['CHEMICAL', 'HEAVY_METAL', 'MERCURY']
             THEN h.description ELSE null END) AS toxicityDescriptions,
         collect(DISTINCT imp.name) AS impacts,
         collect(DISTINCT imp.description) AS impactDescriptions,
         collect(DISTINCT s.name) AS sources

    RETURN
        i.id AS itemId,
        i.label AS label,
        i.labelDisplay AS labelDisplay,

        materials AS materials,
        materialDescriptions AS materialDescriptions,
  
        rule.code AS ruleCode,
        rule.name AS ruleName,
        rule.description AS ruleDescription,

        t.code AS trashTypeCode,
        t.name AS trashTypeName,
        t.description AS trashTypeDescription,

        b.code AS trashBinCode,
        b.name AS trashBinName,
        b.binColorCode AS trashBinColorCode,
        b.description AS trashBinDescription,

        hazards AS hazards,
        hazardDescriptions AS hazardDescriptions,
        toxicityDescriptions AS toxicityDescriptions,
    
        impacts AS impacts,
        impactDescriptions AS impactDescriptions,
    
        tr.code AS treatmentCode,
        tr.name AS treatmentName,
        tr.description AS treatmentDescription,

        guidance AS guidance,
        guidanceDescription AS guidanceDescription,
        guidanceLuY AS guidanceLuY,
        guidanceSteps AS guidanceSteps,

        sources AS sources

    LIMIT 1
    """;

        return neo4jClient.query(cypher)
                .bind(label).to("label")
                .fetch()
                .one()
                .map(this::toDTO);
    }

    @SuppressWarnings("unchecked")
    private TrashReasoningResultDTO toDTO(Map<String, Object> row) {

        List<String> materials =
                (List<String>) row.get("materials");

        List<String> materialDescriptions =
                (List<String>) row.get("materialDescriptions");

        List<String> hazards =
                (List<String>) row.get("hazards");

        List<String> hazardDescriptions =
                (List<String>) row.get("hazardDescriptions");

        List<String> impacts =
                (List<String>) row.get("impacts");

        List<String> impactDescriptions =
                (List<String>) row.get("impactDescriptions");

        List<String> guidanceSteps =
                (List<String>) row.get("guidanceSteps");

        List<String> sources =
                (List<String>) row.get("sources");

        return TrashReasoningResultDTO.builder()

                .itemId(
                        row.get("itemId") == null
                                ? null
                                : ((Number) row.get("itemId")).longValue()
                )

                .label((String) row.get("label"))
                .labelDisplay((String) row.get("labelDisplay"))

                .materials(materials)
                .materialDescriptions(materialDescriptions)

                .ruleCode((String) row.get("ruleCode"))
                .ruleName((String) row.get("ruleName"))
                .ruleDescription((String) row.get("ruleDescription"))

                .trashTypeCode((String) row.get("trashTypeCode"))
                .trashTypeName((String) row.get("trashTypeName"))

                .trashTypeDescription(
                        (String) row.get("trashTypeDescription")
                )

                .trashBinCode((String) row.get("trashBinCode"))
                .trashBinName((String) row.get("trashBinName"))
                .trashBinColorCode((String) row.get("trashBinColorCode"))
                .trashBinDescription(
                        (String) row.get("trashBinDescription")
                )

                .hazards(hazards)
                .hazardDescriptions(hazardDescriptions)
                .toxicityDescriptions((List<String>) row.get("toxicityDescriptions"))

                .impacts(impacts)
                .impactDescriptions(impactDescriptions)

                .treatmentCode((String) row.get("treatmentCode"))
                .treatmentName((String) row.get("treatmentName"))
                .treatmentDescription(
                        (String) row.get("treatmentDescription")
                )

                .guidance((List<String>) row.get("guidance"))
                .guidanceDescription((List<String>) row.get("guidanceDescription"))
                .guidanceLuY((List<String>) row.get("guidanceLuY"))

                .guidanceSteps(guidanceSteps)

                .sources(sources)

                .build();
    }
}